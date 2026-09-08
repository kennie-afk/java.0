package com.smartseason.identity.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.ConflictException;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.UserRepository;
import com.smartseason.identity.team.TeamDtos.InviteRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class TeamServiceTest {

    private UserRepository users;
    private TeamService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new TeamService(users, new BCryptPasswordEncoder(4));
        TenantContext.set(UUID.randomUUID());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(users.existsByEmail(any())).thenReturn(false);
    }

    private static InviteRequest invite(List<String> roles) {
        return new InviteRequest("manager@smartseason.local", "Field Manager",
                "+254700000000", roles, "a-strong-demo-passphrase");
    }

    @Test
    void storesTheRoleAndHashesThePassword() {
        var member = service.invite(invite(List.of("MANAGER")));

        assertThat(member.roles()).containsExactly("MANAGER");
        assertThat(member.email()).isEqualTo("manager@smartseason.local");
    }

    @Test
    void refusesARoleTheServicesDoNotKnow() {
        assertThatThrownBy(() -> service.invite(invite(List.of("SUPERVISOR"))))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("is not a role");
    }

    @Test
    void refusesAnEmptyRoleList() {
        assertThatThrownBy(() -> service.invite(invite(List.of())))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    void normalisesCaseAndRemovesDuplicates() {
        var member = service.invite(invite(List.of("farmer", "FARMER", " manager ")));

        assertThat(member.roles()).containsExactly("FARMER", "MANAGER");
    }

    @Test
    void refusesAnEmailThatAlreadyHasAnAccount() {
        when(users.existsByEmail("manager@smartseason.local")).thenReturn(true);

        assertThatThrownBy(() -> service.invite(invite(List.of("MANAGER"))))
                .isInstanceOf(ConflictException.class);
    }
}
