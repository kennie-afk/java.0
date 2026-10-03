package com.mara.kit.auth;

import com.mara.kit.tenant.TenantContext;
import com.mara.platform.credential.OperatorToken;
import com.mara.platform.credential.Scopes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards {@code /v1/admin/**} (the back office) and {@code /v1/internal/**} (service to service)
 * with scoped, expiring, individually revocable credentials, and binds the tenant.
 *
 * <p>Each endpoint names the ONE scope it needs ({@link #standardRules()}); a path with no rule
 * is refused outright, so a new endpoint is closed until someone decides who may call it. A
 * tenant-bound credential is forced onto its own tenant: the {@code X-Mara-Tenant} a caller
 * sends is overwritten with the credential's, and a different one is refused, so a leaked
 * credential exposes one tenant, not all of them. Only a platform credential (no binding) names
 * the tenant itself.
 *
 * <p>Order: before every other filter, so an unauthenticated caller learns nothing about tenants.
 */
public class OperatorAuthFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Mara-Tenant";

    /** The scope needed for requests whose method matches and whose path matches the pattern. */
    public record Rule(Set<String> methods, Pattern path, String scope) {
        static Rule of(String methods, String path, String scope) {
            return new Rule(methods.isEmpty() ? Set.of() : Set.of(methods.split(",")), Pattern.compile(path), scope);
        }

        boolean applies(String method, String requestPath) {
            return (methods.isEmpty() || methods.contains(method)) && path.matcher(requestPath).matches();
        }
    }

    /** One rule set for every service; a service simply never receives the paths it does not serve. */
    public static List<Rule> standardRules() {
        return List.of(
                Rule.of("POST", "^/v1/admin/tenants$", Scopes.PLATFORM_TENANTS),
                Rule.of("", "^/v1/admin/credentials(/.*)?$", Scopes.CREDENTIALS_MANAGE),
                Rule.of("GET,HEAD", "^/v1/admin(/.*)?$", Scopes.ADMIN_READ),
                Rule.of("POST,PUT,PATCH,DELETE", "^/v1/admin(/.*)?$", Scopes.ADMIN_WRITE),
                Rule.of("GET", "^/v1/internal/terminals/TERM-[0-9A-F]{20}$", Scopes.TERMINALS_LOOKUP),
                Rule.of("POST", "^/v1/internal/credentials/verify$", Scopes.CREDENTIALS_VERIFY),
                Rule.of("GET", "^/v1/internal/chains$", Scopes.SYNC_FEED),
                Rule.of("GET", "^/v1/internal/terminals/[^/]+/entries$", Scopes.SYNC_FEED));
    }

    private final List<Rule> rules;
    private final CredentialVerifier verifier;

    public OperatorAuthFilter(List<Rule> rules, CredentialVerifier verifier) {
        this.rules = rules;
        this.verifier = verifier;
    }

    public OperatorAuthFilter(CredentialVerifier verifier) {
        this(standardRules(), verifier);
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(under(path, "/v1/admin") || under(path, "/v1/internal"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String scope = rules.stream().filter(r -> r.applies(method, path)).map(Rule::scope).findFirst().orElse(null);
        if (scope == null) {
            refuse(response, 403, "no_such_endpoint_scope");
            return;
        }
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        if (OperatorToken.parse(presented).isEmpty()) {
            refuse(response, 401, "unauthorised");
            return;
        }
        CredentialVerifier.Decision d = verifier.verify(new CredentialVerifier.Request(
                presented, scope, trimmed(request.getHeader(TENANT_HEADER)), method, path, request.getRemoteAddr()));
        if (!d.ok()) {
            refuse(response, d.status(), d.status() == 401 ? "unauthorised" : d.reason());
            return;
        }
        request.setAttribute("mara.credential.id", d.credentialId());
        request.setAttribute("mara.credential.label", d.label());
        HttpServletRequest effective = request;
        String tenant = trimmed(request.getHeader(TENANT_HEADER));
        if (d.tenantId() != null) {
            tenant = d.tenantId();
            effective = new TenantHeaderRequest(request, tenant);
        }
        if (tenant != null) {
            TenantContext.set(tenant);
        }
        try {
            chain.doFilter(effective, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String trimmed(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static void refuse(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }

    /** Presents the credential's tenant as the request's {@code X-Mara-Tenant}, whatever the caller sent. */
    private static final class TenantHeaderRequest extends HttpServletRequestWrapper {
        private final String tenant;

        TenantHeaderRequest(HttpServletRequest request, String tenant) {
            super(request);
            this.tenant = tenant;
        }

        @Override
        public String getHeader(String name) {
            return TENANT_HEADER.equalsIgnoreCase(name) ? tenant : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return TENANT_HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(tenant)) : super.getHeaders(name);
        }
    }
}
