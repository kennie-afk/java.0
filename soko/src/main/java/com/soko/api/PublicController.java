package com.soko.api;

import com.soko.domain.AppUser;
import com.soko.domain.Customer;
import com.soko.domain.Tenant;
import com.soko.persistence.CustomerRepository;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.TenantRepository;
import com.soko.persistence.UserRepository;
import com.soko.platform.Errors;
import com.soko.security.Principal;
import com.soko.security.Tokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two storefront views a first-time visitor can reach with no account at
 * all: a public product catalogue, and self-registration against it. Both are
 * resolved by tenant slug rather than by an authenticated principal's tenant,
 * and both are deliberately unauthenticated (see SecurityConfig).
 *
 * <p>Placing an order itself is NOT here on purpose: a buyer signs in or
 * registers first (see {@link #register}), then orders through the normal
 * authenticated {@code /v1/shop/orders}, the same endpoint and the same
 * per-customer order history every other signed-in buyer uses. Registration
 * exists only to remove the friction of needing the owner to provision an
 * account before a first-time buyer can even start - not to let anyone order
 * without one.
 */
@RestController
@RequestMapping("/v1/public")
public class PublicController {

    private final TenantRepository tenants;
    private final OfferRepository offers;
    private final CustomerRepository customers;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Tokens tokens;

    public PublicController(
            TenantRepository tenants,
            OfferRepository offers,
            CustomerRepository customers,
            UserRepository users,
            PasswordEncoder encoder,
            Tokens tokens) {
        this.tenants = tenants;
        this.offers = offers;
        this.customers = customers;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    @GetMapping("/{slug}/products")
    public List<Map<String, Object>> catalogue(@PathVariable String slug) {
        Tenant tenant = tenant(slug);

        return offers.storefront(tenant.getId()).stream()
                .map(
                        r -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", r[0]);
                            row.put("sku", r[1]);
                            row.put("name", r[2]);
                            row.put("category", r[3]);
                            row.put("unit", r[4]);
                            row.put("perishable", r[5]);
                            row.put("chilled", r[6]);
                            row.put("shelfLifeHours", r[7]);
                            row.put("priceCents", r[8]);
                            row.put("inStock", ((Number) r[9]).longValue());
                            return row;
                        })
                .filter(row -> ((Number) row.get("inStock")).longValue() > 0)
                .toList();
    }

    public record CustomerRegistration(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String phone,
            @NotBlank String county,
            @Size(min = 10) String password) {}

    /**
     * Registers a brand new buyer against this storefront and signs them in,
     * in one step - a Customer record and a CUSTOMER-role login are created
     * together, so the account this returns a token for can place an order
     * immediately through {@code /v1/shop/orders}. Signing in an existing
     * buyer needs no separate endpoint here: {@code /v1/auth/login} already
     * works for any role, customers included.
     */
    @PostMapping("/{slug}/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiController.Session register(
            @PathVariable String slug, @Valid @RequestBody CustomerRegistration request) {
        Tenant tenant = tenant(slug);

        if (users.findByEmailAndStatus(request.email(), "ACTIVE").isPresent()) {
            throw new Errors.BadRequest("that email is already registered");
        }

        Customer customer = new Customer();
        customer.setTenantId(tenant.getId());
        customer.setName(request.fullName());
        customer.setPhone(request.phone());
        customer.setCounty(request.county());
        customer = customers.save(customer);

        AppUser user = new AppUser();
        user.setTenantId(tenant.getId());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole("CUSTOMER");
        user.setCustomerId(customer.getId());
        user = users.save(user);

        return session(user, tenant);
    }

    private ApiController.Session session(AppUser user, Tenant tenant) {
        Principal principal =
                new Principal(
                        user.getId(),
                        user.getTenantId(),
                        user.getEmail(),
                        user.getRole(),
                        user.getSupplierId(),
                        user.getCustomerId());
        return new ApiController.Session(
                tokens.issue(principal),
                user.getTenantId().toString(),
                user.getFullName(),
                user.getRole(),
                tenant.getName(),
                tokens.ttlSeconds());
    }

    private Tenant tenant(String slug) {
        return tenants.findBySlug(slug).orElseThrow(() -> new Errors.NotFound("no such storefront"));
    }
}
