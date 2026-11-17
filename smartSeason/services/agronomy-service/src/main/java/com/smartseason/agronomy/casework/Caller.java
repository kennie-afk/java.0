package com.smartseason.agronomy.casework;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The signed-in user as the services see them: an id and the roles in the signed token. */
public record Caller(UUID id, Set<String> roles) {

    public static Caller current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("No authenticated caller");
        }
        return new Caller(UUID.fromString(auth.getName()), auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).collect(Collectors.toSet()));
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return has("ADMIN");
    }

    public boolean isAgronomist() {
        return has("AGRONOMIST");
    }

    /** The role to show beside a message: the most specific one the caller holds. */
    public String displayRole() {
        for (String r : new String[] {"AGRONOMIST", "ADMIN", "FARMER", "MANAGER", "WORKER"}) {
            if (has(r)) {
                return r;
            }
        }
        return "USER";
    }
}
