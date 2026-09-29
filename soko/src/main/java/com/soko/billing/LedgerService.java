package com.soko.billing;

import com.soko.domain.LedgerEntry;
import com.soko.persistence.LedgerEntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional
    public LedgerEntry append(UUID tenantId, String entryType, String referenceType,
            UUID referenceId, long amountCents, String description) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTenantId(tenantId);
        entry.setEntryType(entryType);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setAmountCents(amountCents);
        entry.setDescription(description);
        return entries.save(entry);
    }
}
