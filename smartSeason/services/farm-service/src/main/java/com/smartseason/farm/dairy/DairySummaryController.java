package com.smartseason.farm.dairy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farm/v1/dairy")
@Tag(name = "Dairy", description = "Herd production, deliveries and milk-withdrawal warnings")
public class DairySummaryController {

    private final DairySummaryService service;

    public DairySummaryController(DairySummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'FARMER', 'MANAGER', 'AGRONOMIST')")
    @Operation(summary = "Yield, deliveries, withdrawal warnings and expected calvings for one farm")
    public DairySummary summary(@RequestParam UUID farmId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(farmId, from, to);
    }
}
