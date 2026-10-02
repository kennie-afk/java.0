package com.hms.lab;

import static com.hms.lab.LabModels.*;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.Slice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/lab")
class LabController {

    private static final String READ = "hasAuthority('" + Permissions.LAB_READ + "')";
    private static final String ENTER = "hasAuthority('" + Permissions.LAB_ENTER + "')";
    private static final String VALIDATE = "hasAuthority('" + Permissions.LAB_VALIDATE + "')";
    private static final String MANAGE = "hasAuthority('" + Permissions.LAB_MANAGE + "')";
    private static final String ORDER = "hasAuthority('" + Permissions.ORDERS_WRITE + "')";
    private static final String ACK = "hasAnyAuthority('" + Permissions.ORDERS_WRITE + "', '" + Permissions.LAB_VALIDATE + "')";

    private final LabService lab;

    LabController(LabService lab) {
        this.lab = lab;
    }

    @GetMapping("/tests")
    @PreAuthorize(READ)
    List<Test> tests(@RequestParam(required = false) String q, @RequestParam(defaultValue = "true") boolean activeOnly) {
        return lab.tests(q, activeOnly);
    }

    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    Test createTest(@Valid @RequestBody TestInput in) {
        return lab.createTest(in);
    }

    @PutMapping("/tests/{id}")
    @PreAuthorize(MANAGE)
    Test updateTest(@PathVariable UUID id, @Valid @RequestBody TestInput in) {
        return lab.updateTest(id, in);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ORDER)
    Order order(@Valid @RequestBody OrderInput in) {
        return lab.order(in);
    }

    @GetMapping("/orders")
    @PreAuthorize(READ)
    Slice<OrderRow> list(@RequestParam(required = false) UUID facilityId, @RequestParam(required = false) UUID patientId, @RequestParam(required = false) String status,
                         @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return lab.list(facilityId, patientId, status, cursor, limit);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize(READ)
    Order open(@PathVariable UUID id) {
        return lab.open(id);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ORDERS_WRITE + "', '" + Permissions.LAB_ENTER + "')")
    Order cancel(@PathVariable UUID id, @Valid @RequestBody CancelInput in) {
        return lab.cancel(id, in);
    }

    @PostMapping("/orders/{id}/collect")
    @PreAuthorize(ENTER)
    Order collect(@PathVariable UUID id, @RequestBody(required = false) CollectInput in) {
        return lab.collect(id, in);
    }

    @PostMapping("/items/{id}/result")
    @PreAuthorize(ENTER)
    Item result(@PathVariable UUID id, @Valid @RequestBody ResultInput in) {
        return lab.enterResult(id, in);
    }

    @PostMapping("/items/{id}/validate")
    @PreAuthorize(VALIDATE)
    Item validate(@PathVariable UUID id) {
        return lab.validate(id);
    }

    @PostMapping("/items/{id}/amend")
    @PreAuthorize(VALIDATE)
    Item amend(@PathVariable UUID id, @Valid @RequestBody AmendInput in) {
        return lab.amend(id, in);
    }

    @PostMapping("/items/{id}/acknowledge")
    @PreAuthorize(ACK)
    Item acknowledge(@PathVariable UUID id, @Valid @RequestBody AckInput in) {
        return lab.acknowledge(id, in);
    }

    @GetMapping("/items/{id}/history")
    @PreAuthorize(VALIDATE)
    List<HistoryEntry> history(@PathVariable UUID id) {
        return lab.history(id);
    }

    @GetMapping("/critical")
    @PreAuthorize(READ)
    List<CriticalRow> critical(@RequestParam UUID facilityId) {
        return lab.critical(facilityId);
    }
}
