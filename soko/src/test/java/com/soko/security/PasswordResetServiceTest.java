package com.soko.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soko.domain.AppUser;
import com.soko.domain.PasswordReset;
import com.soko.notifications.EmailNotifier;
import com.soko.persistence.PasswordResetRepository;
import com.soko.persistence.UserRepository;
import com.soko.platform.Errors;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordResetServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetRepository resets = mock(PasswordResetRepository.class);
    private final EmailNotifier email = mock(EmailNotifier.class);
    private final UserStatusGate gate = mock(UserStatusGate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Instant now = Instant.parse("2026-10-06T08:00:00Z");
    private final PasswordResetService service = new PasswordResetService(users, resets, encoder,
            email, gate, Clock.fixed(now, ZoneOffset.UTC), "https://shop.example/", Duration.ofMinutes(30));

    private AppUser user;

    @BeforeEach
    void anAccount() {
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        user.setEmail("amina@example.org");
        user.setFullName("Amina");
        user.setPasswordHash(encoder.encode("old-password-1"));
        when(users.findByEmailAndStatus("amina@example.org", "ACTIVE")).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void anUnknownAddressGetsNothingAndStoresNothing() {
        assertThat(service.issue("nobody@example.org")).isEmpty();
        verify(resets, never()).save(any());
    }

    @Test
    void theTokenIsInTheLinkOnlyAndStoredAsAHashWithAnExpiry() {
        PasswordResetService.Mail mail = service.issue("amina@example.org").orElseThrow();

        Matcher link = Pattern.compile("https://shop\\.example/reset-password\\?token=([A-Za-z0-9_-]{43})").matcher(mail.body());
        assertThat(link.find()).isTrue();
        String token = link.group(1);

        ArgumentCaptor<PasswordReset> saved = ArgumentCaptor.forClass(PasswordReset.class);
        verify(resets).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        verify(resets).voidOutstanding(user.getId(), now);
        assertThat(mail.body()).contains("30 minutes");
    }

    @Test
    void aSendFailureIsSwallowedSoTheResponseCannotRevealTheAccount() {
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down")).when(email).send(any(), any(), any());
        service.deliver(new PasswordResetService.Mail("amina@example.org", "s", "b"));
    }

    @Test
    void aValidTokenChangesThePasswordOnce() {
        PasswordReset stored = new PasswordReset();
        stored.setUserId(user.getId());
        when(resets.findByTokenHash(PasswordResetService.hash("tok"))).thenReturn(Optional.of(stored));
        when(resets.consume(any(), any())).thenReturn(1);

        service.reset("tok", "a-brand-new-password");

        assertThat(encoder.matches("a-brand-new-password", user.getPasswordHash())).isTrue();
        verify(users).save(user);
        verify(gate).forget(user.getId());
    }

    @Test
    void aUsedOrExpiredTokenIsRefusedAndNothingChanges() {
        PasswordReset stored = new PasswordReset();
        stored.setUserId(user.getId());
        when(resets.findByTokenHash(PasswordResetService.hash("tok"))).thenReturn(Optional.of(stored));
        when(resets.consume(any(), any())).thenReturn(0);
        String before = user.getPasswordHash();

        assertThatThrownBy(() -> service.reset("tok", "a-brand-new-password"))
                .isInstanceOf(Errors.BadRequest.class).hasMessageContaining("invalid or has expired");
        assertThat(user.getPasswordHash()).isEqualTo(before);
        verify(users, never()).save(any());
    }

    @Test
    void anUnknownTokenAndASuspendedAccountAreRefused() {
        assertThatThrownBy(() -> service.reset("nope", "a-brand-new-password")).isInstanceOf(Errors.BadRequest.class);

        PasswordReset stored = new PasswordReset();
        stored.setUserId(user.getId());
        when(resets.findByTokenHash(PasswordResetService.hash("tok"))).thenReturn(Optional.of(stored));
        when(resets.consume(any(), any())).thenReturn(1);
        user.setStatus("SUSPENDED");
        assertThatThrownBy(() -> service.reset("tok", "a-brand-new-password")).isInstanceOf(Errors.BadRequest.class);
        verify(users, never()).save(any());
    }
}
