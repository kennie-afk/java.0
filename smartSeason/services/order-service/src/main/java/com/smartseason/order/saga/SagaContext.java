package com.smartseason.order.saga;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class SagaContext {

    private final UUID buyerOrgId;
    private final UUID sellerOrgId;
    private final BigDecimal totalAmount;
    private final String currency;
    private final String idempotencyKey;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public SagaContext(UUID buyerOrgId, UUID sellerOrgId, BigDecimal totalAmount,
                       String currency, String idempotencyKey) {
        this.buyerOrgId = buyerOrgId;
        this.sellerOrgId = sellerOrgId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID buyerOrgId() { return buyerOrgId; }
    public UUID sellerOrgId() { return sellerOrgId; }
    public BigDecimal totalAmount() { return totalAmount; }
    public String currency() { return currency; }
    public String idempotencyKey() { return idempotencyKey; }

    public void put(String key, Object value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
    public Map<String, Object> values() { return Map.copyOf(values); }
}
