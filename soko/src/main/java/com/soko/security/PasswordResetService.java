package com.soko.security;

import com.soko.domain.AppUser;
import com.soko.domain.PasswordReset;
import com.soko.notifications.EmailNotifier;
import com.soko.persistence.PasswordResetRepository;
import com.soko.persistence.UserRepository;
import com.soko.platform.Errors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset by e-mailed link. The token is 256 random bits, stored only as its SHA-256, valid
 * for {@code soko.reset.ttl-minutes}, usable once, and a newer request voids older ones. The
 * request step never says whether the address has an account.
 *
 * <p>Both steps look accounts up across tenants, so the caller wraps them in
 * {@code TenantBinding.asSystem(...)} like sign-in does.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What to send, decided inside the transaction; sending happens after it commits. */
    public record Mail(String to, String subject, String body) {}

    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final PasswordEncoder encoder;
    private final EmailNotifier email;
    private final UserStatusGate statusGate;
    private final Clock clock;
    private final String publicUrl;
    private final Duration ttl;

    @Autowired
    public PasswordResetService(UserRepository users, PasswordResetRepository resets,
            PasswordEncoder encoder, EmailNotifier email, UserStatusGate statusGate,
            @Value("${soko.public-url:http://localhost:3500}") String publicUrl,
            @Value("${soko.reset.ttl-minutes:30}") long ttlMinutes) {
        this(users, resets, encoder, email, statusGate, Clock.systemUTC(), publicUrl,
                Duration.ofMinutes(ttlMinutes));
    }

    PasswordResetService(UserRepository users, PasswordResetRepository resets,
            PasswordEncoder encoder, EmailNotifier email, UserStatusGate statusGate, Clock clock,
            String publicUrl, Duration ttl) {
        this.users = users;
        this.resets = resets;
        this.encoder = encoder;
        this.email = email;
        this.statusGate = statusGate;
        this.clock = clock;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.ttl = ttl;
    }

    /** Stores a fresh token for an active account; empty when the address has none. */
    @Transactional
    public Optional<Mail> issue(String address) {
        Optional<AppUser> found = users.findByEmailAndStatus(address, "ACTIVE");
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = found.get();
        Instant now = clock.instant();
        resets.voidOutstanding(user.getId(), now);

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordReset reset = new PasswordReset();
        reset.setTenantId(user.getTenantId());
        reset.setUserId(user.getId());
        reset.setTokenHash(hash(token));
        reset.setExpiresAt(now.plus(ttl));
        resets.save(reset);

        String link = publicUrl + "/reset-password?token=" + token;
        return Optional.of(new Mail(user.getEmail(), "Reset your FreshFerm password",
                "Hello " + user.getFullName() + ",\n\n"
                        + "Someone asked to reset the password on this account. To choose a new one, open:\n\n"
                        + link + "\n\n"
                        + "The link works once and expires in " + ttl.toMinutes() + " minutes. "
                        + "If you did not ask for this, ignore this message; your password has not changed.\n"));
    }

    /** Sends the message; a failure is logged and swallowed so the response cannot reveal the account. */
    public void deliver(Mail mail) {
        try {
            email.send(mail.to(), mail.subject(), mail.body());
        } catch (RuntimeException failure) {
            log.error("password reset e-mail could not be sent", failure);
        }
    }

    @Transactional
    public void reset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        PasswordReset reset = resets.findByTokenHash(hash(token.trim())).orElseThrow(PasswordResetService::invalid);
        Instant now = clock.instant();
        // The conditional update is the single-use guarantee: of two simultaneous submissions only
        // one sees a row change.
        if (resets.consume(reset.getId(), now) != 1) {
            throw invalid();
        }
        AppUser user = users.findById(reset.getUserId())
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(PasswordResetService::invalid);
        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        resets.voidOutstanding(user.getId(), now);
        statusGate.forget(user.getId());
    }

    private static Errors.BadRequest invalid() {
        return new Errors.BadRequest("this reset link is invalid or has expired; request a new one");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
