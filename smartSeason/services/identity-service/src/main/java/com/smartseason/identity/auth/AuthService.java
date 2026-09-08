package com.smartseason.identity.auth;

import com.smartseason.identity.auth.AuthDtos.LoginRequest;
import com.smartseason.identity.auth.AuthDtos.ProfileResponse;
import com.smartseason.identity.auth.AuthDtos.RefreshRequest;
import com.smartseason.identity.auth.AuthDtos.RegisterRequest;
import com.smartseason.identity.auth.AuthDtos.TokenResponse;
import com.smartseason.identity.domain.Organisation;
import com.smartseason.identity.domain.RefreshToken;
import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.ConflictException;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.repo.OrganisationRepository;
import com.smartseason.identity.repo.RefreshTokenRepository;
import com.smartseason.identity.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long REFRESH_TTL_DAYS = 30;
    /**
     * What the person who registers an organisation gets.
     *
     * They are always ADMIN of the organisation they just created - somebody has
     * to be able to invite the rest - and they also get the role that matches
     * what the organisation does, so a farm owner can work their own farm
     * without granting themselves anything.
     */
    private static String rolesFor(Organisation.OrgType orgType) {
        return switch (orgType) {
            case FARM, COOPERATIVE -> "ADMIN,FARMER";
            case BUYER -> "ADMIN,BUYER";
            case TRANSPORTER -> "ADMIN,STOREKEEPER";
            case ADMIN -> "ADMIN";
        };
    }

    private final OrganisationRepository organisations;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final EventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public AuthService(OrganisationRepository organisations,
                       UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       TokenService tokens,
                       EventPublisher events) {
        this.organisations = organisations;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.events = events;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request, String userAgent, String ip) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account already exists for that email address");
        }

        UUID tenantId = UUID.randomUUID();

        Organisation organisation = new Organisation();
        organisation.setId(tenantId);
        organisation.setTenantId(tenantId);
        organisation.setName(request.organisationName());
        organisation.setOrgType(parseOrgType(request.orgType()));
        organisation.setPhone(request.phone());
        organisation.setEmail(email);
        organisation.setStatus(Organisation.Status.ACTIVE);
        organisation.setKycStatus(Organisation.KycStatus.NONE);
        organisations.save(organisation);

        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setOrganisationId(tenantId);
        user.setRoles(rolesFor(organisation.getOrgType()));
        user.setStatus(User.Status.ACTIVE);
        user.setMfaEnabled(false);
        user.setFailedAttempts(0);
        user.setLocale("en");
        User saved = users.save(user);

        events.publish("identity", "OrgCreated", tenantId, organisation.getName());
        events.publish("identity", "UserRegistered", saved.getId(), saved.getEmail());

        return issueTokens(saved, userAgent, ip);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String userAgent, String ip) {
        User user = users.findByEmail(normalise(request.email()))
                .orElseThrow(() -> new DomainRuleException("Invalid email or password"));

        if (user.getStatus() == User.Status.LOCKED) {
            throw new DomainRuleException("This account is locked; contact an administrator");
        }
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new DomainRuleException("This account is suspended");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() == null ? 1 : user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(User.Status.LOCKED);
            }
            users.save(user);
            throw new DomainRuleException("Invalid email or password");
        }

        user.setFailedAttempts(0);
        user.setLastLoginAt(Instant.now());
        users.save(user);

        return issueTokens(user, userAgent, ip);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request, String userAgent, String ip) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new DomainRuleException("Invalid refresh token"));

        if (stored.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(stored.getUserId(), Instant.now());
            throw new DomainRuleException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainRuleException("Refresh token has expired");
        }

        stored.setRevokedAt(Instant.now());
        refreshTokens.save(stored);

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUserId()));

        return issueTokens(user, userAgent, ip);
    }

    public ProfileResponse profile(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return new ProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getOrganisationId(), user.getRoles(),
                user.getStatus() == null ? null : user.getStatus().name(), user.getLocale());
    }

    private TokenResponse issueTokens(User user, String userAgent, String ip) {
        String accessToken = tokens.mintAccessToken(
                user.getId(), user.getTenantId(), user.getRoles());

        byte[] raw = new byte[48];
        random.nextBytes(raw);
        String refreshValue = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken token = new RefreshToken();
        token.setTenantId(user.getTenantId());
        token.setUserId(user.getId());
        token.setTokenHash(hash(refreshValue));
        token.setExpiresAt(Instant.now().plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS));
        token.setUserAgent(truncate(userAgent, 255));
        token.setIp(truncate(ip, 64));
        refreshTokens.save(token);

        return new TokenResponse(accessToken, refreshValue, "Bearer",
                tokens.accessTtlSeconds(), user.getId(), user.getOrganisationId(), user.getRoles());
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private static Organisation.OrgType parseOrgType(String value) {
        if (value == null || value.isBlank()) {
            return Organisation.OrgType.FARM;
        }
        try {
            return Organisation.OrgType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainRuleException("Unknown organisation type: " + value);
        }
    }

    private static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
