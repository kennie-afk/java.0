package com.soko.security;

import com.soko.domain.OtpCode;
import com.soko.notifications.SmsSender;
import com.soko.persistence.OtpCodeRepository;
import com.soko.platform.Errors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Generates and checks phone verification codes. Kept apart from
 * {@code PublicController} for the same reason {@code RoutingEngine} is kept
 * apart from the order endpoints: the rules here are worth being able to
 * reason about, and test, on their own.
 *
 * <p>A 6-digit code has nowhere near the entropy of a real credential -
 * 1,000,000 possibilities, not 2^128 - so unlike a password or an API key,
 * the code itself is not what makes this safe. What does: a 5 minute
 * lifetime, a hard cap of 5 verification attempts before the code is dead,
 * and at most one live code per phone number at a time (requesting a new one
 * kills any earlier unconsumed code, so a captured old code buys an attacker
 * nothing extra).
 */
@Service
public class OtpService {

    private static final int VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final OtpCodeRepository codes;
    private final SmsSender sms;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository codes, SmsSender sms) {
        this.codes = codes;
        this.sms = sms;
    }

    public void request(UUID tenantId, String phone) {
        Instant now = Instant.now();
        for (OtpCode existing : codes.findByTenantIdAndPhoneOrderByCreatedAtDesc(tenantId, phone)) {
            if (existing.getConsumedAt() == null && existing.getExpiresAt().isAfter(now)) {
                existing.setExpiresAt(now);
                codes.save(existing);
            }
        }

        String code = generate();
        OtpCode entry = new OtpCode();
        entry.setTenantId(tenantId);
        entry.setPhone(phone);
        entry.setCodeHash(hash(code));
        entry.setExpiresAt(now.plus(VALIDITY_MINUTES, ChronoUnit.MINUTES));
        codes.save(entry);

        sms.send(
                phone,
                "Your FreshFerm verification code is "
                        + code
                        + ". It expires in "
                        + VALIDITY_MINUTES
                        + " minutes.");
    }

    /** Throws if the code is missing, expired, already used, exhausted, or wrong. */
    public void verify(UUID tenantId, String phone, String submitted) {
        List<OtpCode> candidates = codes.findByTenantIdAndPhoneOrderByCreatedAtDesc(tenantId, phone);
        OtpCode latest = candidates.isEmpty() ? null : candidates.get(0);

        if (latest == null) {
            throw new Errors.BadRequest("request a code first");
        }
        if (latest.getConsumedAt() != null) {
            throw new Errors.BadRequest("that code has already been used; request a new one");
        }
        if (!latest.getExpiresAt().isAfter(Instant.now())) {
            throw new Errors.BadRequest("that code has expired; request a new one");
        }
        if (latest.getAttempts() >= MAX_ATTEMPTS) {
            throw new Errors.BadRequest("too many attempts; request a new code");
        }

        if (!matches(submitted, latest.getCodeHash())) {
            latest.setAttempts(latest.getAttempts() + 1);
            codes.save(latest);
            throw new Errors.BadRequest("that code is not correct");
        }

        latest.setConsumedAt(Instant.now());
        codes.save(latest);
    }

    private String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean matches(String submitted, String storedHash) {
        return MessageDigest.isEqual(
                hash(submitted).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
