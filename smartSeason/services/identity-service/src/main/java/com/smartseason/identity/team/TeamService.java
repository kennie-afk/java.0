package com.smartseason.identity.team;

import com.smartseason.identity.domain.User;
import com.smartseason.identity.platform.ConflictException;
import com.smartseason.identity.platform.DomainRuleException;
import com.smartseason.identity.platform.ResourceNotFoundException;
import com.smartseason.identity.platform.TenantContext;
import com.smartseason.identity.repo.UserRepository;
import com.smartseason.identity.team.TeamDtos.InviteRequest;
import com.smartseason.identity.team.TeamDtos.MemberResponse;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding people to an organisation and setting what they may do.
 *
 * The role names here must match the ones the services are annotated with. An
 * unknown role would silently grant nothing, which looks like a broken account
 * rather than a rejected request, so it is refused outright.
 */
@Service
@Transactional(readOnly = true)
public class TeamService {

    /** Must stay in step with tools/rbac.py. */
    static final Set<String> KNOWN_ROLES = Set.of(
            "ADMIN", "FARMER", "MANAGER", "AGRONOMIST",
            "STOREKEEPER", "FINANCE", "BUYER", "WORKER");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TeamService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public List<MemberResponse> list() {
        return users.findAllByTenantId(TenantContext.requireTenantId(),
                        org.springframework.data.domain.Pageable.ofSize(500))
                .map(TeamService::toResponse)
                .getContent();
    }

    @Transactional
    public MemberResponse invite(InviteRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account already exists for that email address");
        }

        User user = new User();
        user.setTenantId(TenantContext.requireTenantId());
        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(normalise(request.roles()));
        user.setStatus(User.Status.ACTIVE);
        user.setMfaEnabled(false);
        user.setFailedAttempts(0);

        return toResponse(users.save(user));
    }

    @Transactional
    public MemberResponse setRoles(UUID userId, List<String> roles) {
        User user = users.findByIdAndTenantId(userId, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setRoles(normalise(roles));
        return toResponse(users.save(user));
    }

    private static String normalise(List<String> roles) {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String role : roles) {
            String upper = role.trim().toUpperCase(Locale.ROOT);
            if (!KNOWN_ROLES.contains(upper)) {
                throw new DomainRuleException(
                        "'" + role + "' is not a role. Known roles: " + String.join(", ", KNOWN_ROLES));
            }
            cleaned.add(upper);
        }
        if (cleaned.isEmpty()) {
            throw new DomainRuleException("A user needs at least one role");
        }
        return String.join(",", cleaned);
    }

    private static MemberResponse toResponse(User user) {
        List<String> roles = user.getRoles() == null
                ? List.of()
                : Arrays.stream(user.getRoles().split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
        return new MemberResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), roles, user.getStatus().name());
    }
}
