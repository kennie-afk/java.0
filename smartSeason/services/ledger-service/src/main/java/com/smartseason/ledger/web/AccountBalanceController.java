package com.smartseason.ledger.web;

import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.service.AccountBalanceService;
import com.smartseason.ledger.web.dto.AccountBalanceCreateRequest;
import com.smartseason.ledger.web.dto.AccountBalanceResponse;
import com.smartseason.ledger.web.dto.AccountBalanceUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger/v1/account-balances")
@Tag(name = "AccountBalance", description = "Double-entry accounts, immutable postings, balances, statements")
public class AccountBalanceController {

    private final AccountBalanceService service;

    public AccountBalanceController(AccountBalanceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List account-balances for the caller's tenant")
    public PageResponse<AccountBalanceResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single AccountBalance by id")
    public AccountBalanceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Create a AccountBalance")
    public ResponseEntity<AccountBalanceResponse> create(@Valid @RequestBody AccountBalanceCreateRequest request) {
        AccountBalanceResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/ledger/v1/account-balances/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    @Operation(summary = "Apply a partial update to a AccountBalance")
    public AccountBalanceResponse update(@PathVariable UUID id, @Valid @RequestBody AccountBalanceUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Delete a AccountBalance")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
