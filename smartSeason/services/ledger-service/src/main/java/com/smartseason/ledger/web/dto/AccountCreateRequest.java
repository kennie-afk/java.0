package com.smartseason.ledger.web.dto;

import com.smartseason.ledger.domain.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AccountCreateRequest(
        @NotBlank @Size(max = 255) String accountCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull Account.AccountType accountType,
        UUID ownerOrgId,
        UUID ownerUserId,
        @NotBlank @Size(max = 255) String currency,
        @NotNull Account.NormalBalance normalBalance,
        @NotNull Account.Status status,
        UUID parentAccountId) {
}
