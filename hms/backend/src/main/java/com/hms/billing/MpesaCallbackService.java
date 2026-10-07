package com.hms.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.platform.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Receives Safaricom's STK confirmation. The body shape (Body.stkCallback with CheckoutRequestID, ResultCode and a
 * CallbackMetadata item list) is taken from the public Daraja documentation and is UNVERIFIED against live Safaricom traffic.
 *
 * Contract with the caller (the controller): a returned value means "acknowledge"; an exception means "answer 5xx so Safaricom
 * retries". Only input that cannot be understood at all is acknowledged without effect, and every acknowledged-but-unapplied
 * payment is kept in mpesa_unclaimed, so a 200 never means the money was lost.
 */
@Service
public class MpesaCallbackService {

    private static final Logger log = LoggerFactory.getLogger("hms.mpesa");

    public enum Result { APPLIED, UNCLAIMED, MALFORMED }

    private final JdbcClient jdbc;
    private final BillingService billing;
    private final ObjectMapper json;

    MpesaCallbackService(JdbcClient jdbc, BillingService billing, ObjectMapper json) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.json = json;
    }

    private record Found(UUID orgId, UUID paymentId, UUID facilityId) {}

    public Result handle(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            log.warn("M-Pesa callback ignored: the body is not JSON");
            return Result.MALFORMED;
        }
        JsonNode cb = root == null ? null : root.path("Body").path("stkCallback");
        String checkout = cb == null ? "" : cb.path("CheckoutRequestID").asText("");
        if (cb == null || cb.isMissingNode() || checkout.isBlank() || checkout.length() > 100 || !cb.path("ResultCode").canConvertToInt()) {
            log.warn("M-Pesa callback ignored: no Body.stkCallback with CheckoutRequestID and ResultCode");
            return Result.MALFORMED;
        }
        int code = cb.path("ResultCode").asInt();
        String desc = cb.path("ResultDesc").asText(null);
        String receipt = null;
        BigDecimal amount = null;
        String phone = null;
        for (JsonNode item : cb.path("CallbackMetadata").path("Item")) {
            switch (item.path("Name").asText("")) {
                case "MpesaReceiptNumber" -> receipt = item.path("Value").asText(null);
                case "Amount" -> amount = item.path("Value").isNumber() ? item.path("Value").decimalValue() : null;
                case "PhoneNumber" -> phone = item.path("Value").asText(null);
                default -> { }
            }
        }
        // A database failure below is not caught: the controller turns it into a 5xx and Safaricom retries.
        Found found = jdbc.sql("SELECT * FROM mpesa_find_payment(?)").param(checkout)
                .query((rs, n) -> new Found(rs.getObject("org_id", UUID.class), rs.getObject("payment_id", UUID.class), rs.getObject("facility_id", UUID.class))).optional().orElse(null);
        if (found == null) {
            keep("UNKNOWN_REFERENCE", cb, checkout, receipt, amount, phone, code, desc, null, null, body);
            return Result.UNCLAIMED;
        }
        String reason;
        TenantContext.set(new TenantContext.Tenant(found.orgId(), null, Set.of(found.facilityId()), Set.of()));
        try {
            reason = billing.applyMpesaCallback(found.paymentId(), code, desc, receipt, amount);
        } finally {
            TenantContext.clear();
        }
        if (reason == null) {
            return Result.APPLIED;
        }
        keep(reason, cb, checkout, receipt, amount, phone, code, desc, found.orgId(), found.paymentId(), body);
        return Result.UNCLAIMED;
    }

    private void keep(String reason, JsonNode cb, String checkout, String receipt, BigDecimal amount, String phone, int code, String desc,
                      UUID orgId, UUID paymentId, String raw) {
        jdbc.sql("""
                INSERT INTO mpesa_unclaimed (reason, checkout_request_id, merchant_request_id, receipt, amount, phone, result_code, result_desc, org_id, payment_id, raw)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING""")
                .params(reason, checkout, cb.path("MerchantRequestID").asText(null), receipt, amount, phone, code,
                        desc == null ? null : desc.substring(0, Math.min(desc.length(), 500)), orgId, paymentId, raw).update();
        log.warn("M-Pesa callback kept unclaimed ({}), checkout {}", reason, checkout);
    }
}
