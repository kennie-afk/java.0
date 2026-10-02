package com.hms.platform.security;

import com.hms.platform.rbac.AccessService;
import com.hms.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a bearer token into a tenant scope and a set of authorities for exactly one request. The
 * tenant is bound to the thread before any transaction starts, so the database transaction is
 * stamped with the right organisation, and it is always cleared afterwards.
 */
@Component
class TokenFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AccessService access;

    TokenFilter(JwtService jwt, AccessService access) {
        this.jwt = jwt;
        this.access = access;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var claims = jwt.parse(header.substring(7).trim());
            if (claims.isPresent()) {
                var granted = access.resolve(claims.get().orgId(), claims.get().practitionerId());
                if (granted.active()) {
                    TenantContext.set(new TenantContext.Tenant(
                            claims.get().orgId(), claims.get().practitionerId(), granted.facilityIds(), granted.permissions()));
                    List<SimpleGrantedAuthority> authorities =
                            granted.permissions().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(claims.get().practitionerId(), null, authorities));
                }
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
