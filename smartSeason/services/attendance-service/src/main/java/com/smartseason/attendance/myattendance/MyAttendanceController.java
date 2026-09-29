package com.smartseason.attendance.myattendance;

import com.smartseason.attendance.web.dto.ClockEventResponse;
import com.smartseason.attendance.web.dto.ShiftResponse;
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
@RequestMapping("/api/attendance/v1/my-attendance")
@Tag(name = "My attendance", description = "A worker's own clock events and shifts")
public class MyAttendanceController {

    private final MyAttendanceService service;

    public MyAttendanceController(MyAttendanceService service) {
        this.service = service;
    }

    @GetMapping("/clock-events")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "Clock events belonging to the signed-in account")
    public List<ClockEventResponse> myClockEvents(Authentication authentication) {
        return service.myClockEvents(callerId(authentication)).stream()
                .map(ClockEventResponse::from).toList();
    }

    @GetMapping("/clock-events/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "One of the signed-in account's own clock events")
    public ClockEventResponse myClockEvent(@PathVariable UUID id, Authentication authentication) {
        return ClockEventResponse.from(service.myClockEvent(id, callerId(authentication)));
    }

    @GetMapping("/shifts")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "Shifts belonging to the signed-in account")
    public List<ShiftResponse> myShifts(Authentication authentication) {
        return service.myShifts(callerId(authentication)).stream()
                .map(ShiftResponse::from).toList();
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKER')")
    @Operation(summary = "One of the signed-in account's own shifts")
    public ShiftResponse myShift(@PathVariable UUID id, Authentication authentication) {
        return ShiftResponse.from(service.myShift(id, callerId(authentication)));
    }

    private static UUID callerId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
