package com.soko.inventory;

import com.soko.domain.Offer;
import com.soko.domain.WastageRecord;
import com.soko.persistence.OfferRepository;
import com.soko.persistence.WastageRecordRepository;
import com.soko.platform.Errors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WastageService {

    private final OfferRepository offers;
    private final WastageRecordRepository wastage;

    public WastageService(OfferRepository offers, WastageRecordRepository wastage) {
        this.offers = offers;
        this.wastage = wastage;
    }

    /** Records stock as lost and removes it from what's sellable, in one step. */
    @Transactional
    public WastageRecord record(UUID tenantId, UUID offerId, int quantity, String reason) {
        if (quantity <= 0) {
            throw new Errors.BadRequest("wastage quantity must be positive");
        }
        Offer offer = offers.findByIdAndTenantId(offerId, tenantId)
                .orElseThrow(() -> new Errors.NotFound("no such offer"));

        if (offers.reserve(offerId, quantity) != 1) {
            throw new Errors.BadRequest(
                    "cannot record wastage of " + quantity + ": only "
                            + offer.getAvailableQty() + " available");
        }

        WastageRecord record = new WastageRecord();
        record.setTenantId(tenantId);
        record.setOfferId(offerId);
        record.setProductId(offer.getProductId());
        record.setSupplierId(offer.getSupplierId());
        record.setQuantity(quantity);
        record.setReason(reason == null || reason.isBlank() ? "EXPIRED" : reason.toUpperCase());
        record.setValueCents(offer.getCostCents() * quantity);
        return wastage.save(record);
    }

    public long totalValueCents(UUID tenantId) {
        return wastage.totalValueCents(tenantId);
    }
}
