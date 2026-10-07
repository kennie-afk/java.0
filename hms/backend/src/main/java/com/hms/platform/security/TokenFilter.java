package com.hms.platform.security;

import com.hms.platform.rbac.AccessService;
import com.hms.platform.rbac.PortalAccessGate;
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

    static final String PORTAL_SELF = "portal:self";

    private final JwtService jwt;
    private final AccessService access;
    private final PortalAccessGate portalGate;

    TokenFilter(JwtService jwt, AccessService access, PortalAccessGate portalGate) {
        this.jwt = jwt;
        this.access = access;
        this.portalGate = portalGate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean portalPath = request.getRequestURI().startsWith("/portal/");
        if (header != null && header.startsWith("Bearer ") && portalPath) {
            // The portal accepts only portal tokens, and nothing else accepts them: staff paths never see a patient session.
            jwt.parsePortal(header.substring(7).trim()).ifPresent(c -> {
                TenantContext.set(new TenantContext.Tenant(c.orgId(), c.accountId(), java.util.Set.of(), java.util.Set.of(PORTAL_SELF)));
                // A valid signature is not enough: a facility that disables an account expects it to stop working now,
                // not when the token expires.
                if (!portalGate.active(c.accountId())) {
                    TenantContext.clear();
                    return;
                }
                PortalSession.set(c.patientId());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(c.accountId(), null, List.of(new SimpleGrantedAuthority(PORTAL_SELF))));
            });
        } else if (header != null && header.startsWith("Bearer ")) {
            var claims = jwt.parse(header.substring(7).trim());
            if (claims.isPresent()) {
                var granted = access.resolve(claims.get().orgId(), claims.get().practitionerId());
                // A signature that checks out is not enough: the person may have been disabled, or their sessions ended, since it was issued.
                if (granted.accepts(claims.get().issuedAtMillis())) {
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
            PortalSession.clear();
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
