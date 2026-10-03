package com.hms.imaging;

import static com.hms.imaging.ImagingModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/imaging")
class ImagingController {

    private static final String READ = "hasAuthority('" + Permissions.IMAGING_READ + "')";
    private static final String PERFORM = "hasAuthority('" + Permissions.IMAGING_PERFORM + "')";
    private static final String SIGN = "hasAuthority('" + Permissions.IMAGING_SIGN + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.IMAGING_MANAGE + "')";
    private static final String ORDER = "hasAuthority('" + Permissions.ORDERS_WRITE + "')";

    private final ImagingService imaging;

    ImagingController(ImagingService imaging) {
        this.imaging = imaging;
    }

    @GetMapping("/procedures")
    @PreAuthorize(READ)
    List<Procedure> procedures(@RequestParam(required = false) String q, @RequestParam(defaultValue = "true") boolean activeOnly) {
        return imaging.procedures(q, activeOnly);
    }

    @PostMapping("/procedures")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Procedure createProcedure(@Valid @RequestBody ProcedureInput in) {
        return imaging.createProcedure(in);
    }

    @PutMapping("/procedures/{id}")
    @PreAuthorize(MANAGE)
    Procedure updateProcedure(@PathVariable UUID id, @Valid @RequestBody ProcedureInput in) {
        return imaging.updateProcedure(id, in);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ORDER)
    Order order(@Valid @RequestBody OrderInput in) {
        return imaging.order(in);
    }

    @GetMapping("/orders")
    @PreAuthorize(READ)
    Slice<OrderRow> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String status,
                         @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return imaging.list(facilityId, patientId, status, cursor, limit);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize(READ)
    Order open(@PathVariable UUID id) {
        return imaging.open(id);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ORDERS_WRITE + "', '" + Permissions.IMAGING_PERFORM + "')")
    Order cancel(@PathVariable UUID id, @Valid @RequestBody CancelInput in) {
        return imaging.cancel(id, in);
    }

    @PostMapping("/orders/{id}/perform")
    @PreAuthorize(PERFORM)
    Order perform(@PathVariable UUID id, @Valid @RequestBody(required = false) PerformInput in) {
        return imaging.perform(id, in);
    }

    @PostMapping("/orders/{id}/report")
    @PreAuthorize(PERFORM)
    Order report(@PathVariable UUID id, @Valid @RequestBody ReportInput in) {
        return imaging.report(id, in);
    }

    @PostMapping("/orders/{id}/sign")
    @PreAuthorize(SIGN)
    Order sign(@PathVariable UUID id) {
        return imaging.sign(id);
    }

    @PostMapping("/orders/{id}/amend")
    @PreAuthorize(SIGN)
    Order amend(@PathVariable UUID id, @Valid @RequestBody AmendInput in) {
        return imaging.amend(id, in);
    }

    @PostMapping("/orders/{id}/acknowledge")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ORDERS_WRITE + "', '" + Permissions.IMAGING_SIGN + "')")
    Order acknowledge(@PathVariable UUID id, @Valid @RequestBody AckInput in) {
        return imaging.acknowledge(id, in);
    }

    @GetMapping("/orders/{id}/history")
    @PreAuthorize(SIGN)
    List<HistoryEntry> history(@PathVariable UUID id) {
        return imaging.history(id);
    }

    @GetMapping("/critical")
    @PreAuthorize(READ)
    List<CriticalRow> critical(@RequestParam UUID facilityId) {
        return imaging.critical(facilityId);
    }
}
