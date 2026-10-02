package com.hms.reporting;

import static com.hms.reporting.ReportingService.*;

import com.hms.platform.rbac.Permissions;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reports")
@PreAuthorize("hasAuthority('" + Permissions.REPORTS_READ + "')")
class ReportingController {

    private final ReportingService reports;

    ReportingController(ReportingService reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    Overview overview(@RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.overview(facilityId, from, to);
    }

    @GetMapping("/outpatient")
    Outpatient outpatient(@RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.outpatient(facilityId, from, to);
    }

    @GetMapping("/finance")
    Finance finance(@RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.finance(facilityId, from, to);
    }

    @GetMapping("/inpatient")
    Inpatient inpatient(@RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.inpatient(facilityId, from, to);
    }

    @GetMapping("/laboratory")
    Laboratory laboratory(@RequestParam UUID facilityId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.laboratory(facilityId, from, to);
    }
}
