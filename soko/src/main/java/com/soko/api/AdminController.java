package com.soko.api;

import com.soko.domain.AppUser;
import com.soko.persistence.UserRepository;
import com.soko.platform.Errors;
import com.soko.payment.PaymentService;
import com.soko.security.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only read endpoints behind the console's team and money-attention screens. */
@RestController
@RequestMapping("/v1")
public class AdminController {

    private final UserRepository users;
    private final PaymentService payments;
    private final TenantContext context;

    public AdminController(UserRepository users, PaymentService payments, TenantContext context) {
        this.users = users;
        this.payments = payments;
        this.context = context;
    }

    private void ownerOnly(String what) {
        if (!"OWNER".equals(context.current().role())) {
            throw new Errors.Unauthorized("only an owner can " + what);
        }
    }

    /** The accounts of this tenant (never the password hash), for the suspend/reactivate screen. */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {
        ownerOnly("list accounts");
        var found = users.search(context.current().tenantId(), Paging.like(q), Paging.pageable(page, limit));
        List<Map<String, Object>> rows = found.getContent().stream().map(AdminController::row).toList();
        return Paging.respond(rows, found.getTotalElements(), page, limit);
    }

    private static Map<String, Object> row(AppUser user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("email", user.getEmail());
        row.put("fullName", user.getFullName());
        row.put("role", user.getRole());
        row.put("status", user.getStatus());
        row.put("supplierId", user.getSupplierId());
        row.put("customerId", user.getCustomerId());
        return row;
    }

    /**
     * M-Pesa payments that succeeded but could not be applied to an order or invoice. Each is money
     * received with nothing to show for it, so the owner has to resolve it (usually a refund).
     */
    @GetMapping("/payments/orphaned")
    public List<Map<String, Object>> orphaned(@RequestParam(defaultValue = "50") int limit) {
        ownerOnly("see unmatched payments");
        return payments.orphaned(context.current().tenantId(), Paging.limit(limit)).stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("purpose", p.getPurpose());
            row.put("referenceId", p.getReferenceId());
            row.put("msisdn", p.getMsisdn());
            row.put("amountCents", p.getAmountCents());
            row.put("receipt", p.getMpesaReceiptNumber());
            row.put("reason", p.getOrphanReason());
            row.put("initiatedAt", p.getInitiatedAt());
            row.put("completedAt", p.getCompletedAt());
            return row;
        }).toList();
    }
}
