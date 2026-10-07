package com.smartseason.payment.collect;

import com.smartseason.payment.domain.PaymentIntent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The entry point for taking money. {@link MpesaCollectionService} has always been able to push an STK
 * prompt, record the intent and the transaction and publish the event, but nothing called it: the
 * generic payment-intents CRUD only wrote a row, so no push was ever sent. This is the call that does.
 *
 * <p>Idempotent on the caller's {@code idempotencyKey}: repeating a request returns the intent already
 * created and sends no second prompt. The answer says where the payment stands, and the outcome itself
 * arrives later, through the secret-authenticated callback.
 *
 * <p>202 when the prompt was sent and the payer has yet to answer, 422 when the push was refused (the
 * intent is kept, marked failed, with the reason), 200 for a request already seen.
 */
@RestController
@RequestMapping("/api/payment/v1/collections")
@Tag(name = "Collection", description = "Ask a payer to pay by M-Pesa STK push")
public class CollectionController {

    private final MpesaCollectionService service;

    public CollectionController(MpesaCollectionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'BUYER')")
    @Operation(summary = "Send an M-Pesa STK push for a payment intent")
    public ResponseEntity<Map<String, Object>> collect(@Valid @RequestBody CollectionRequest request) {
        PaymentIntent intent = service.collect(request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", intent.getId());
        body.put("reference", intent.getReference());
        body.put("status", intent.getStatus());
        if (intent.getProviderRef() != null) body.put("checkoutRequestId", intent.getProviderRef());
        if (intent.getFailureReason() != null) body.put("failureReason", intent.getFailureReason());

        HttpStatus status = switch (intent.getStatus()) {
            case PENDING -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(body);
    }
}
