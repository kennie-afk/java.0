package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.intake.BulkIntakeRequest;
import com.kenyarealestate.verification.dto.intake.BulkIntakeResponse;
import com.kenyarealestate.verification.dto.ownership.*;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.security.JwtUtil;
import com.kenyarealestate.verification.service.BulkIntakeService;
import com.kenyarealestate.verification.service.PropertyOwnershipVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/verification/ownership")
public class PropertyOwnershipController {

    private final PropertyOwnershipVerificationService service;
    private final BulkIntakeService bulkIntakeService;
    private final JwtUtil jwtUtil;

    public PropertyOwnershipController(PropertyOwnershipVerificationService service,
                                       BulkIntakeService bulkIntakeService,
                                       JwtUtil jwtUtil) {
        this.service = service;
        this.bulkIntakeService = bulkIntakeService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/start")
    public ResponseEntity<OwnershipVerificationResponse> start(
            HttpServletRequest httpReq, @Valid @RequestBody StartOwnershipVerificationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startOwnershipVerification(resolveUserId(httpReq), req));
    }

    @PostMapping("/{verificationId}/documents")
    public ResponseEntity<OwnershipVerificationResponse> uploadDocument(
            HttpServletRequest httpReq,
            @PathVariable UUID verificationId,
            @Valid @RequestBody UploadOwnershipDocumentRequest req) {
        return ResponseEntity.ok(service.uploadDocument(resolveUserId(httpReq), verificationId, req));
    }

    /**
     * Files a whole folder at once, with no category declared for any document.
     *
     * <p>The single-document endpoint above asks the seller to pick from sixteen
     * categories per file. This one works it out, files everything it is sure about, and
     * returns a short list of what still needs a person — which for a clean upload is
     * empty.
     */
    @PostMapping("/{verificationId}/documents/bulk")
    public ResponseEntity<BulkIntakeResponse> bulkUpload(
            HttpServletRequest httpReq,
            @PathVariable UUID verificationId,
            @Valid @RequestBody BulkIntakeRequest req) {
        return ResponseEntity.ok(
                bulkIntakeService.intakeOwnership(resolveUserId(httpReq), verificationId, req));
    }

    @DeleteMapping("/{verificationId}/documents/{documentId}")
    public ResponseEntity<OwnershipVerificationResponse> deleteDocument(
            HttpServletRequest httpReq, @PathVariable UUID verificationId, @PathVariable UUID documentId) {
        return ResponseEntity.ok(service.deleteDocument(resolveUserId(httpReq), verificationId, documentId));
    }

    @PostMapping("/{verificationId}/submit")
    public ResponseEntity<OwnershipVerificationResponse> submit(
            HttpServletRequest httpReq, @PathVariable UUID verificationId) {
        return ResponseEntity.ok(service.submitForReview(resolveUserId(httpReq), verificationId));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<OwnershipVerificationResponse> getByProperty(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(service.getByPropertyId(propertyId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<OwnershipVerificationResponse>> getMyVerifications(
            HttpServletRequest httpReq,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        // A plain array, as callers already parse it, with the total in headers. Bounded: a seller
        // with thousands of filings is not a case this endpoint should have to survive unbounded.
        Page<OwnershipVerificationResponse> found = service.getByUserId(resolveUserId(httpReq),
                PageRequest.of(Math.max(page, 0), clampSize(size, MAX_PAGE_SIZE)));
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(found.getTotalElements()))
                .header("X-Has-More", Boolean.toString(found.hasNext()))
                .body(found.getContent());
    }

    @PostMapping("/internal/{verificationId}/ai-screening")
    public ResponseEntity<OwnershipVerificationResponse> aiScreening(
            @PathVariable UUID verificationId, @RequestBody OwnershipDocumentLegalCheckRequest req) {
        return ResponseEntity.ok(service.processAiScreening(verificationId, req));
    }

    @GetMapping("/admin/queue")
    public ResponseEntity<Page<OwnershipVerificationResponse>> adminQueue(
            @RequestParam(defaultValue = "HUMAN_REVIEW") OwnershipVerificationStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Sort.Direction dir = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        // The size is capped and the sort column whitelisted: the caller chose neither a
        // page that loads the whole table nor an arbitrary property name to sort by.
        String column = QUEUE_SORT_COLUMNS.contains(sortBy) ? sortBy : "createdAt";
        return ResponseEntity.ok(service.getQueue(status,
                PageRequest.of(Math.max(page, 0), clampSize(size, MAX_QUEUE_SIZE),
                        Sort.by(dir, column).and(Sort.by("id")))));
    }

    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_QUEUE_SIZE = 500;
    private static final Set<String> QUEUE_SORT_COLUMNS = Set.of("createdAt", "updatedAt", "status");

    static int clampSize(int requested, int max) {
        return Math.min(Math.max(requested, 1), max);
    }

    @PutMapping("/admin/{verificationId}/ministry-check")
    public ResponseEntity<OwnershipVerificationResponse> ministryCheck(
            @PathVariable UUID verificationId,
            @RequestParam boolean ministryConfirmed,
            @RequestParam(required = false) String notes,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.recordMinistryCheck(
                verificationId, ministryConfirmed, resolveUserId(httpReq), notes));
    }

    @PutMapping("/admin/{verificationId}/encumbrance-check")
    public ResponseEntity<OwnershipVerificationResponse> encumbranceCheck(
            @PathVariable UUID verificationId,
            @RequestParam boolean encumbranceClear,
            @RequestParam(required = false) String notes,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.recordEncumbranceCheck(
                verificationId, encumbranceClear, resolveUserId(httpReq), notes));
    }

    @PutMapping("/admin/{verificationId}/legal-check")
    public ResponseEntity<OwnershipVerificationResponse> legalCheck(
            @PathVariable UUID verificationId,
            @RequestBody OwnershipDocumentLegalCheckRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.submitLegalCheckResults(
                verificationId, req, resolveUserId(httpReq)));
    }

    @PutMapping("/admin/{verificationId}/final-decision")
    public ResponseEntity<OwnershipVerificationResponse> finalDecision(
            @PathVariable UUID verificationId,
            @Valid @RequestBody AdminOwnershipReviewRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.ok(service.adminFinalDecision(
                verificationId, req, resolveUserId(httpReq), extractToken(httpReq)));
    }

    private UUID resolveUserId(HttpServletRequest request) {
        UUID fromAttr = (UUID) request.getAttribute("authenticatedUserId");
        if (fromAttr != null) return fromAttr;
        String token = extractToken(request);
        if (token != null) return jwtUtil.extractUserId(token);
        throw new com.kenyarealestate.verification.exception.VerificationException(
                "Could not resolve authenticated user identity");
    }

    private String extractToken(HttpServletRequest r) {
        String h = r.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
