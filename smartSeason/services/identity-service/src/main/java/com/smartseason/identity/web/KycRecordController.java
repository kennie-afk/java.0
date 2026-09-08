package com.smartseason.identity.web;

import com.smartseason.identity.platform.PageResponse;
import com.smartseason.identity.service.KycRecordService;
import com.smartseason.identity.web.dto.KycRecordCreateRequest;
import com.smartseason.identity.web.dto.KycRecordResponse;
import com.smartseason.identity.web.dto.KycRecordUpdateRequest;
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
@RequestMapping("/api/identity/v1/kyc-records")
@Tag(name = "KycRecord", description = "Users, organisations, roles, sessions, KYC, JWT issuance + JWKS")
public class KycRecordController {

    private final KycRecordService service;

    public KycRecordController(KycRecordService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "List kyc-records for the caller's tenant")
    public PageResponse<KycRecordResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Fetch a single KycRecord by id")
    public KycRecordResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Create a KycRecord")
    public ResponseEntity<KycRecordResponse> create(@Valid @RequestBody KycRecordCreateRequest request) {
        KycRecordResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/identity/v1/kyc-records/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER')")
    @Operation(summary = "Apply a partial update to a KycRecord")
    public KycRecordResponse update(@PathVariable UUID id, @Valid @RequestBody KycRecordUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Delete a KycRecord")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
