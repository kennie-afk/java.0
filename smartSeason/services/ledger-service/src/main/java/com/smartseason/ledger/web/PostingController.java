package com.smartseason.ledger.web;

import com.smartseason.ledger.platform.PageResponse;
import com.smartseason.ledger.service.PostingService;
import com.smartseason.ledger.web.dto.PostingCreateRequest;
import com.smartseason.ledger.web.dto.PostingResponse;
import com.smartseason.ledger.web.dto.PostingUpdateRequest;
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
@RequestMapping("/api/ledger/v1/postings")
@Tag(name = "Posting", description = "Double-entry accounts, immutable postings, balances, statements")
public class PostingController {

    private final PostingService service;

    public PostingController(PostingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "List postings for the caller's tenant")
    public PageResponse<PostingResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'FINANCE')")
    @Operation(summary = "Fetch a single Posting by id")
    public PostingResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Create a Posting")
    public ResponseEntity<PostingResponse> create(@Valid @RequestBody PostingCreateRequest request) {
        PostingResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/ledger/v1/postings/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Apply a partial update to a Posting")
    public PostingResponse update(@PathVariable UUID id, @Valid @RequestBody PostingUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    @Operation(summary = "Delete a Posting")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
