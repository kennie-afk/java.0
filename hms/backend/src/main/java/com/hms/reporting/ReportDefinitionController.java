package com.hms.reporting;

import static com.hms.reporting.ReportDefinitionModels.*;

import com.hms.platform.rbac.Permissions;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/report-definitions")
class ReportDefinitionController {

    private static final String READ = "hasAuthority('" + Permissions.REPORTS_READ + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.REPORTS_MANAGE + "')";

    private final ReportDefinitionService reports;

    ReportDefinitionController(ReportDefinitionService reports) {
        this.reports = reports;
    }

    @GetMapping("/measures")
    @PreAuthorize(READ)
    List<MeasureInfo> measures() {
        return reports.measures();
    }

    @GetMapping
    @PreAuthorize(READ)
    List<Definition> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return reports.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    Definition get(@PathVariable UUID id) {
        return reports.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Definition create(@Valid @RequestBody DefinitionInput in) {
        return reports.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    Definition update(@PathVariable UUID id, @Valid @RequestBody DefinitionInput in) {
        return reports.update(id, in);
    }

    @GetMapping("/{id}/run")
    @PreAuthorize(READ)
    RunResult run(@PathVariable UUID id, @RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.run(id, facilityId, from, to);
    }

    @GetMapping("/{id}/export.csv")
    @PreAuthorize(READ)
    ResponseEntity<byte[]> csv(@PathVariable UUID id, @RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + from + "-" + to + ".csv\"")
                .body(reports.csv(id, facilityId, from, to).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/export.dhis2")
    @PreAuthorize(READ)
    Map<String, Object> dhis2(@PathVariable UUID id, @RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dhis2(id, facilityId, from, to);
    }
}
