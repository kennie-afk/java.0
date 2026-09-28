package com.soko.api;

import com.soko.domain.AppUser;
import com.soko.domain.Customer;
import com.soko.domain.Tenant;
import com.soko.persistence.CustomerRepository;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.TenantRepository;
import com.soko.persistence.UserRepository;
import com.soko.platform.Errors;
import com.soko.security.OtpService;
import com.soko.security.Principal;
import com.soko.security.Tokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * The storefront views a first-time visitor can reach with no account at
 * all: a public product catalogue, and phone verification to open one. Both
 * are resolved by tenant slug rather than by an authenticated principal's
 * tenant, and both are deliberately unauthenticated (see SecurityConfig).
 *
 * <p>Placing an order itself is NOT here on purpose: a buyer verifies their
 * phone first (which opens an account on the spot if this number has never
 * ordered here before), then orders through the normal authenticated
 * {@code /v1/shop/orders}, the same endpoint and the same per-customer order
 * history every other signed-in buyer uses.
 *
 * <p>Phone + a one-time code, not email + password: almost nobody in this
 * market authenticates with a password day to day, but everyone already
 * trusts a code sent to their phone from M-Pesa, banking apps and everything
 * else. This is the storefront's own identity mechanism - staff and owners
 * still sign in with email and password at {@code /v1/auth/login}, which is
 * unaffected.
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
    private final OtpService otp;

    public PublicController(
            TenantRepository tenants,
            OfferRepository offers,
            CustomerRepository customers,
            UserRepository users,
            PasswordEncoder encoder,
            Tokens tokens,
            OtpService otp) {
        this.tenants = tenants;
        this.offers = offers;
        this.customers = customers;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.otp = otp;
    }

    private static final int STOREFRONT_LIMIT = 500;

    @GetMapping("/{slug}/products")
    public List<Map<String, Object>> catalogue(@PathVariable String slug) {
        Tenant tenant = tenant(slug);

        return offers.storefront(tenant.getId(), STOREFRONT_LIMIT).stream()
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

    public record OtpRequest(@NotBlank String phone) {}

    @PostMapping("/{slug}/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> requestOtp(@PathVariable String slug, @Valid @RequestBody OtpRequest request) {
        Tenant tenant = tenant(slug);
        otp.request(tenant.getId(), request.phone().trim());
        return Map.of("detail", "a verification code has been sent");
    }

    public record OtpVerify(
            @NotBlank String phone, @NotBlank String code, String fullName, String county) {}

    /**
     * Verifies the code and signs the buyer in. If this phone has never
     * ordered from this tenant before, {@code fullName} and {@code county}
     * must be supplied and a Customer + CUSTOMER-role login are created on
     * the spot; if it has, those are ignored and the existing account is
     * used, so a returning buyer only ever needs their phone and the code.
     */
    @PostMapping("/{slug}/otp/verify")
    public ApiController.Session verifyOtp(@PathVariable String slug, @Valid @RequestBody OtpVerify request) {
        Tenant tenant = tenant(slug);
        String phone = request.phone().trim();
        List<Customer> existing = customers.findByTenantIdAndPhone(tenant.getId(), phone);

        // Checked before the code is spent, not after: these are pure input
        // validation that doesn't depend on the code at all, and a buyer who
        // typed the right code but forgot their name should get to fix that
        // and resubmit with the SAME code, not be told to request a new one
        // for something the code was never wrong about.
        if (existing.isEmpty()) {
            if (request.fullName() == null || request.fullName().isBlank()) {
                throw new Errors.BadRequest("enter your name to finish creating your account");
            }
            if (request.county() == null || request.county().isBlank()) {
                throw new Errors.BadRequest("enter your county to finish creating your account");
            }
        }

        otp.verify(tenant.getId(), phone, request.code().trim());

        Customer customer;
        if (!existing.isEmpty()) {
            customer = existing.get(0);
        } else {
            Customer created = new Customer();
            created.setTenantId(tenant.getId());
            created.setName(request.fullName().trim());
            created.setPhone(phone);
            created.setCounty(request.county().trim());
            customer = customers.save(created);
        }

        List<AppUser> existingUser = users.findByTenantIdAndCustomerId(tenant.getId(), customer.getId());
        AppUser user;
        if (!existingUser.isEmpty()) {
            user = existingUser.get(0);
        } else {
            AppUser created = new AppUser();
            created.setTenantId(tenant.getId());
            created.setEmail(syntheticEmail(tenant, phone));
            created.setFullName(customer.getName());
            // Never checked: this account is only ever reached through OTP
            // verification, never through /v1/auth/login's password check.
            // Hashing random bytes (rather than a fixed placeholder) means no
            // two accounts share a crackable password, on the off chance
            // password login is ever accidentally re-enabled for this role.
            created.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            created.setRole("CUSTOMER");
            created.setCustomerId(customer.getId());
            user = users.save(created);
        }

        return session(user, tenant);
    }

    private String syntheticEmail(Tenant tenant, String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        return "otp+" + digits + "@" + tenant.getSlug() + ".phone.local";
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
