package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.service.RentalIncomeTaxService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kenyarealestate.pms.exception.ConflictException;

/** The signed-in landlord's own monthly rental income tax readiness. Nothing here is a filing. */
@RestController
@RequestMapping("/api/mri")
public class RentalIncomeTaxController {

    private final RentalIncomeTaxService service;
    private final CallerIdentity caller;

    public RentalIncomeTaxController(RentalIncomeTaxService service, CallerIdentity caller) {
        this.service = service;
        this.caller = caller;
    }

    @Operation(summary = "Rent received in a month, the tax the configured rate makes of it, and the due date")
    @GetMapping("/summary")
    public ResponseEntity<RentalIncomeTaxService.Summary> summary(
            @RequestParam(required = false) String month, HttpServletRequest r) {
        return ResponseEntity.ok(service.summary(caller.userId(r), parse(month)));
    }

    @Operation(summary = "Return-ready CSV of every confirmed rent receipt in a month, with totals")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String month, HttpServletRequest r) {
        YearMonth m = parse(month);
        byte[] body = service.csv(caller.userId(r), m).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rental-income-" + m + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @Operation(summary = "Set your own MRI rate (percent) once confirmed with KRA; null clears it back to the default")
    @PutMapping("/rate")
    public ResponseEntity<Map<String, Object>> setRate(@RequestBody Map<String, BigDecimal> body, HttpServletRequest r) {
        BigDecimal rate = service.setRate(caller.userId(r), body.get("ratePercent"));
        return ResponseEntity.ok(Map.of("ratePercent", rate));
    }

    private static YearMonth parse(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now().minusMonths(1);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ConflictException("Month must look like 2026-09.");
        }
    }
}
