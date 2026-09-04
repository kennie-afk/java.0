package com.kenyarealestate.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.dto.C2bCallbackRequest;
import com.kenyarealestate.payment.security.CallbackIpPolicy;
import com.kenyarealestate.payment.security.CallbackSecurity;
import com.kenyarealestate.payment.service.C2bService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/mpesa/c2b")
public class C2bController {

    private final C2bService c2bService;
    private final CallbackIpPolicy callbackIpPolicy;
    private final ObjectMapper mapper;

    @Value("${mpesa.callback-secret}")
    private String callbackSecret;

    public C2bController(C2bService c2bService, CallbackIpPolicy callbackIpPolicy, ObjectMapper mapper) {
        this.c2bService = c2bService;
        this.callbackIpPolicy = callbackIpPolicy;
        this.mapper = mapper;
    }

    @Operation(summary = "M-Pesa C2B validation: is this account number one of ours?",
               description = "Called by Safaricom before accepting a paybill payment. Registered with Safaricom as the validation URL; not for external clients.")
    @PostMapping("/validation/{secret}")
    public ResponseEntity<Map<String, String>> validation(@PathVariable String secret,
                                                          @RequestBody String rawBody,
                                                          HttpServletRequest r) {
        if (!guard(secret, r)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        C2bCallbackRequest req = parse(rawBody);
        if (req == null) {
            return ResponseEntity.ok(verdict(C2bService.REJECTED_INVALID_ACCOUNT, "Could not read that request."));
        }
        C2bService.Verdict v = c2bService.validate(req, rawBody);
        return ResponseEntity.ok(verdict(v.resultCode(), v.resultDesc()));
    }

    @Operation(summary = "M-Pesa C2B confirmation: money has arrived",
               description = "Called by Safaricom once a paybill payment succeeds. Always answers 0 — refusing money that has already moved would make Safaricom retry forever.")
    @PostMapping("/confirmation/{secret}")
    public ResponseEntity<Map<String, String>> confirmation(@PathVariable String secret,
                                                            @RequestBody String rawBody,
                                                            HttpServletRequest r) {
        if (!guard(secret, r)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        C2bCallbackRequest req = parse(rawBody);
        if (req == null) {
            log.error("ALERT: unreadable C2B confirmation body; money may have arrived unrecorded: {}", rawBody);
            return ResponseEntity.ok(verdict(C2bService.ACCEPTED, "Received"));
        }
        C2bService.Verdict v = c2bService.confirm(req, rawBody);
        return ResponseEntity.ok(verdict(v.resultCode(), v.resultDesc()));
    }

    private boolean guard(String secret, HttpServletRequest r) {
        String callerIp = getClientIp(r);
        if (!CallbackSecurity.secretMatches(secret, callbackSecret)) {
            log.warn("C2B callback with invalid secret from IP {}", callerIp);
            return false;
        }
        if (!callbackIpPolicy.isAllowed(callerIp)) {
            log.warn("C2B callback from disallowed IP {}", callerIp);
            return false;
        }
        return true;
    }

    private C2bCallbackRequest parse(String rawBody) {
        try {
            return mapper.readValue(rawBody, C2bCallbackRequest.class);
        } catch (Exception e) {
            log.error("Could not parse C2B payload: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> verdict(String code, String desc) {
        return Map.of("ResultCode", code, "ResultDesc", desc);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
