package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AccountUpdateRequest(
        @Size(max = 255) String accountCode,
        @Size(max = 255) String name,
        Account.AccountType accountType,
        UUID ownerOrgId,
        UUID ownerUserId,
        @Size(max = 255) String currency,
        Account.NormalBalance normalBalance,
        Account.Status status,
        UUID parentAccountId) {
}
