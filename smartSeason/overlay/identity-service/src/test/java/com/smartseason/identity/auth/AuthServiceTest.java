package com.smartseason.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.identity.auth.AuthDtos.LoginRequest;
import com.smartseason.identity.auth.AuthDtos.RegisterRequest;
import com.smartseason.identity.auth.AuthDtos.TokenResponse;
import com.smartseason.identity.domain.Organisation;
import com.smartseason.identity.domain.RefreshToken;
import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.ConflictException;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.platform.EventPublisher;
import com.smartseason.identity.repo.OrganisationRepository;
import com.smartseason.identity.repo.RefreshTokenRepository;
import com.smartseason.identity.repo.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String SECRET =
            "a-test-signing-key-that-is-comfortably-longer-than-sixty-four-bytes-for-hmac";

    private final OrganisationRepository organisations = mock(OrganisationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final EventPublisher events = mock(EventPublisher.class);
    private final TokenService tokens = new TokenService(SECRET, "smartseason-identity", 15);

    private final AuthService service = new AuthService(
            organisations, users, refreshTokens, passwordEncoder, tokens, events);

    private RegisterRequest registration() {
        return new RegisterRequest("Green Acres", "Jane Farmer", "Jane@Example.COM",
                "a-strong-passphrase", "+254700000000", "FARM");
    }

    private void stubSaves() {
        when(organisations.save(any(Organisation.class))).thenAnswer(i -> i.getArgument(0));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });
    }

    @Test
    @DisplayName("registration creates the organisation as its own tenant and issues tokens")
    void registrationCreatesTenant() {
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        stubSaves();

        ArgumentCaptor<Organisation> orgCaptor = ArgumentCaptor.forClass(Organisation.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        TokenResponse response = service.register(registration(), "junit", "127.0.0.1");

        verify(organisations).save(orgCaptor.capture());
        verify(users).save(userCaptor.capture());

        Organisation organisation = orgCaptor.getValue();
        User user = userCaptor.getValue();

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        assertThat(organisation.getId())
                .as("the organisation is its own tenant root")
                .isEqualTo(organisation.getTenantId());
        assertThat(user.getTenantId()).isEqualTo(organisation.getId());
        assertThat(response.organisationId()).isEqualTo(organisation.getId());

        assertThat(user.getEmail())
                .as("email is normalised before storage")
                .isEqualTo("jane@example.com");
        assertThat(user.getPasswordHash())
                .as("the raw password is never stored")
                .isNotEqualTo("a-strong-passphrase");
        assertThat(passwordEncoder.matches("a-strong-passphrase", user.getPasswordHash())).isTrue();

        verify(events).publish("identity", "OrgCreated", organisation.getId(), "Green Acres");
        verify(events).publish("identity", "UserRegistered", user.getId(), "jane@example.com");
    }

    @Test
    @DisplayName("registration rejects an email that is already taken")
    void duplicateEmailRejected() {
        when(users.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registration(), "junit", "127.0.0.1"))
                .isInstanceOf(ConflictException.class);

        verify(users, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a wrong password is rejected with the same message as an unknown account")
    void wrongPasswordIsIndistinguishable() {
        User user = existingUser("a-strong-passphrase");
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() ->
                service.login(new LoginRequest("jane@example.com", "wrong"), "junit", "127.0.0.1"))
                .isInstanceOf(DomainRuleException.class)
                .hasMessage("Invalid email or password");

        assertThatThrownBy(() -> {
            when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
            service.login(new LoginRequest("nobody@example.com", "whatever"), "junit", "127.0.0.1");
        }).isInstanceOf(DomainRuleException.class).hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("the account locks after five consecutive failures")
    void accountLocksAfterRepeatedFailures() {
        User user = existingUser("a-strong-passphrase");
        user.setFailedAttempts(4);
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() ->
                service.login(new LoginRequest("jane@example.com", "wrong"), "junit", "127.0.0.1"))
                .isInstanceOf(DomainRuleException.class);

        assertThat(user.getStatus()).isEqualTo(User.Status.LOCKED);
    }

    @Test
    @DisplayName("a correct password clears the failure counter and stamps the login time")
    void successfulLoginResetsCounters() {
        User user = existingUser("a-strong-passphrase");
        user.setFailedAttempts(3);
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        stubSaves();

        TokenResponse response =
                service.login(new LoginRequest("jane@example.com", "a-strong-passphrase"),
                        "junit", "127.0.0.1");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("a signing key shorter than 64 bytes is refused at construction")
    void shortSigningKeyRejected() {
        assertThatThrownBy(() -> new TokenService("too-short", "smartseason-identity", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    private User existingUser(String rawPassword) {
        UUID tenant = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant);
        user.setOrganisationId(tenant);
        user.setEmail("jane@example.com");
        user.setFullName("Jane Farmer");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles("ADMIN");
        user.setStatus(User.Status.ACTIVE);
        user.setFailedAttempts(0);
        user.setCreatedAt(Instant.now());
        return user;
    }
}
