package com.soko.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    public Principal current() {
        Object value = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (value instanceof Principal principal) {
            return principal;
        }
        throw new IllegalStateException("no tenant on the request");
    }
}
