package com.smartseason.attendance.myattendance;

import com.smartseason.attendance.domain.ClockEvent;
import com.smartseason.attendance.domain.Shift;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.MyClockEventRepository;
import com.smartseason.attendance.repo.MyShiftRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A worker's own clock events and shifts. Read-only: clock events are a
 * fraud-detection input recorded by the device/geofence pipeline, so a
 * worker who could edit their own would be able to rewrite the evidence
 * against them. Same ownership shape as task-service's MyWorkService -
 * confirming a specific row exists is itself information a worker should
 * not get about someone else's attendance, so a mismatch is reported as
 * not-found rather than forbidden.
 */
@Service
@Transactional(readOnly = true)
public class MyAttendanceService {

    private final MyClockEventRepository clockEvents;
    private final MyShiftRepository shifts;

    public MyAttendanceService(MyClockEventRepository clockEvents, MyShiftRepository shifts) {
        this.clockEvents = clockEvents;
        this.shifts = shifts;
    }

    public List<ClockEvent> myClockEvents(UUID callerUserId) {
        return clockEvents.findAllByTenantIdAndWorkerIdOrderByOccurredAtDesc(
                TenantContext.requireTenantId(), callerUserId);
    }

    public ClockEvent myClockEvent(UUID id, UUID callerUserId) {
        return clockEvents.findByIdAndTenantIdAndWorkerId(id, TenantContext.requireTenantId(), callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("ClockEvent", id));
    }

    public List<Shift> myShifts(UUID callerUserId) {
        return shifts.findAllByTenantIdAndWorkerIdOrderByStartedAtDesc(
                TenantContext.requireTenantId(), callerUserId);
    }

    public Shift myShift(UUID id, UUID callerUserId) {
        return shifts.findByIdAndTenantIdAndWorkerId(id, TenantContext.requireTenantId(), callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", id));
    }
}
