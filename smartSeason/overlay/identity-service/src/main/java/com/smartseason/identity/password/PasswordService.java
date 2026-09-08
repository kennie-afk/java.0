package com.smartseason.identity.password;

import com.smartseason.identity.domain.OtpChallenge;
import com.smartseason.identity.domain.User;
import com.smartseason.identity.password.PasswordDtos.ChangePasswordRequest;
import com.smartseason.identity.password.PasswordDtos.ForgotPasswordResponse;
import com.smartseason.identity.password.PasswordDtos.ResetPasswordRequest;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.repo.PasswordResetRepository;
import com.smartseason.identity.repo.SessionRevocationRepository;
import com.smartseason.identity.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changing and resetting passwords.
 *
 * Two rules shape this class. A reset code is stored only as a hash, so a leak
 * of the database does not hand over live codes. And "forgot password" answers
 * identically whether or not the address is registered, so it cannot be used to
 * discover who holds an account.
 */
@Service
@Transactional(readOnly = true)
public class PasswordService {

    static final String PURPOSE = "PASSWORD_RESET";
    private static final long CODE_TTL_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository users;
    private final PasswordResetRepository challenges;
    private final SessionRevocationRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * When true, the reset code is returned in the response so the flow can be
     * exercised without an SMS or mail provider wired up. It must be false
     * anywhere real people sign in.
     */
    private final boolean exposeCode;

    public PasswordService(UserRepository users,
                           PasswordResetRepository challenges,
                           SessionRevocationRepository refreshTokens,
                           PasswordEncoder passwordEncoder,
                           @Value("${smartseason.auth.expose-reset-code:false}") boolean exposeCode) {
        this.users = users;
        this.challenges = challenges;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.exposeCode = exposeCode;
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new DomainRuleException("The current password is not correct");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new DomainRuleException("The new password must differ from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);

        // Every other session is ended: if the reason for the change was that
        // someone else had the old password, leaving their session alive
        // defeats the point.
        refreshTokens.deleteAll(refreshTokens.findAllByUserId(userId));
    }

    @Transactional
    public ForgotPasswordResponse requestReset(String email) {
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> found = users.findByEmail(normalised);

        String code = null;
        if (found.isPresent()) {
            User user = found.get();
            code = String.format("%06d", random.nextInt(1_000_000));

            OtpChallenge challenge = new OtpChallenge();
            challenge.setTenantId(user.getTenantId());
            challenge.setUserId(user.getId());
            challenge.setDestination(normalised);
            challenge.setChannel(OtpChallenge.Channel.EMAIL);
            challenge.setCodeHash(sha256(code));
            challenge.setPurpose(PURPOSE);
            challenge.setExpiresAt(Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES));
            challenge.setAttempts(0);
            challenges.save(challenge);
        }

        return new ForgotPasswordResponse(
                "If that address has an account, a reset code has been sent to it.",
                exposeCode ? code : null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String normalised = request.email().trim().toLowerCase(Locale.ROOT);

        OtpChallenge challenge = challenges
                .findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        normalised, PURPOSE)
                .orElseThrow(() -> new DomainRuleException("That reset code is not valid"));

        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainRuleException("That reset code has expired. Request a new one.");
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw new DomainRuleException("Too many attempts. Request a new code.");
        }

        if (!constantTimeEquals(sha256(request.code()), challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            challenges.save(challenge);
            throw new DomainRuleException("That reset code is not valid");
        }

        User user = users.findById(challenge.getUserId())
                .orElseThrow(() -> new DomainRuleException("That reset code is not valid"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setFailedAttempts(0);
        users.save(user);

        challenge.setConsumedAt(Instant.now());
        challenges.save(challenge);

        refreshTokens.deleteAll(refreshTokens.findAllByUserId(user.getId()));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
