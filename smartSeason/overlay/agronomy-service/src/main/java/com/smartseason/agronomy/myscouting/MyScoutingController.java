package com.smartseason.agronomy.myscouting;

import com.smartseason.agronomy.web.dto.ScoutingReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agronomy/v1/my-scouting-reports")
@Tag(name = "My scouting reports", description = "Scouting reports filed by the signed-in worker")
public class MyScoutingController {

    private final MyScoutingService service;

    public MyScoutingController(MyScoutingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "Scouting reports filed by the signed-in account")
    public List<ScoutingReportResponse> mine(Authentication authentication) {
        return service.mine(callerId(authentication)).stream()
                .map(ScoutingReportResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "One of the signed-in account's own scouting reports")
    public ScoutingReportResponse one(@PathVariable UUID id, Authentication authentication) {
        return ScoutingReportResponse.from(service.one(id, callerId(authentication)));
    }

    private static UUID callerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
