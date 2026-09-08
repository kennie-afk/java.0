package com.smartseason.identity.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.identity.domain.OtpChallenge;
import com.smartseason.identity.domain.User;
import com.smartseason.identity.password.PasswordDtos.ChangePasswordRequest;
import com.smartseason.identity.password.PasswordDtos.ResetPasswordRequest;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.repo.PasswordResetRepository;
import com.smartseason.identity.repo.SessionRevocationRepository;
import com.smartseason.identity.repo.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordServiceTest {

    private UserRepository users;
    private PasswordResetRepository challenges;
    private SessionRevocationRepository sessions;
    private PasswordEncoder encoder;
    private PasswordService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        challenges = mock(PasswordResetRepository.class);
        sessions = mock(SessionRevocationRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new PasswordService(users, challenges, sessions, encoder, true);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("farmer@smartseason.local");
        user.setPasswordHash(encoder.encode("the-current-passphrase"));
        user.setFailedAttempts(3);

        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByEmail("farmer@smartseason.local")).thenReturn(Optional.of(user));
        when(sessions.findAllByUserId(any())).thenReturn(List.of());
    }

    @Test
    void changingAPasswordRequiresTheCurrentOne() {
        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("not-the-password", "a-brand-new-passphrase")))
                .isInstanceOf(DomainRuleException.class);
        verify(users, never()).save(any());
    }

    @Test
    void theNewPasswordMustDifferFromTheOld() {
        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("the-current-passphrase", "the-current-passphrase")))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    void aValidChangeReplacesTheHashAndEndsOtherSessions() {
        service.changePassword(user.getId(),
                new ChangePasswordRequest("the-current-passphrase", "a-brand-new-passphrase"));

        assertThat(encoder.matches("a-brand-new-passphrase", user.getPasswordHash())).isTrue();
        verify(sessions).deleteAll(any());
    }

    @Test
    void forgotPasswordAnswersTheSameForAnUnknownAddress() {
        when(users.findByEmail("nobody@smartseason.local")).thenReturn(Optional.empty());

        var known = service.requestReset("farmer@smartseason.local");
        var unknown = service.requestReset("nobody@smartseason.local");

        assertThat(known.message()).isEqualTo(unknown.message());
        // Only the real account gets a code, and no challenge is stored for the other.
        assertThat(known.devCode()).isNotNull();
        assertThat(unknown.devCode()).isNull();
    }

    @Test
    void aWrongResetCodeIsRefusedAndCounted() {
        OtpChallenge challenge = challenge("123456");
        when(challenges.findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "farmer@smartseason.local", PasswordService.PURPOSE)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(
                "farmer@smartseason.local", "000000", "a-brand-new-passphrase")))
                .isInstanceOf(DomainRuleException.class);

        assertThat(challenge.getAttempts()).isEqualTo(1);
    }

    @Test
    void anExpiredCodeIsRefused() {
        OtpChallenge challenge = challenge("123456");
        challenge.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(challenges.findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "farmer@smartseason.local", PasswordService.PURPOSE)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(
                "farmer@smartseason.local", "123456", "a-brand-new-passphrase")))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void aCorrectCodeSetsThePasswordAndIsConsumedOnce() {
        OtpChallenge challenge = challenge("123456");
        when(challenges.findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "farmer@smartseason.local", PasswordService.PURPOSE)).thenReturn(Optional.of(challenge));

        service.resetPassword(new ResetPasswordRequest(
                "farmer@smartseason.local", "123456", "a-brand-new-passphrase"));

        assertThat(encoder.matches("a-brand-new-passphrase", user.getPasswordHash())).isTrue();
        assertThat(challenge.getConsumedAt()).isNotNull();
        assertThat(user.getFailedAttempts()).isZero();
        verify(sessions).deleteAll(any());
    }

    private OtpChallenge challenge(String code) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.setTenantId(user.getTenantId());
        challenge.setUserId(user.getId());
        challenge.setDestination(user.getEmail());
        challenge.setChannel(OtpChallenge.Channel.EMAIL);
        challenge.setPurpose(PasswordService.PURPOSE);
        challenge.setCodeHash(sha256(code));
        challenge.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        challenge.setAttempts(0);
        return challenge;
    }

    private static String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
