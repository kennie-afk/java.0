package com.smartseason.attendance.myattendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartseason.attendance.domain.ClockEvent;
import com.smartseason.attendance.domain.Shift;
import com.smartseason.attendance.platform.ResourceNotFoundException;
import com.smartseason.attendance.platform.TenantContext;
import com.smartseason.attendance.repo.MyClockEventRepository;
import com.smartseason.attendance.repo.MyShiftRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyAttendanceServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AMINA = UUID.randomUUID();
    private static final UUID JOSEPH = UUID.randomUUID();

    private MyClockEventRepository clockEvents;
    private MyShiftRepository shifts;
    private MyAttendanceService service;
    private ClockEvent clockEvent;
    private Shift shift;

    @BeforeEach
    void setUp() {
        clockEvents = mock(MyClockEventRepository.class);
        shifts = mock(MyShiftRepository.class);
        service = new MyAttendanceService(clockEvents, shifts);
        TenantContext.set(TENANT);

        clockEvent = new ClockEvent();
        clockEvent.setId(UUID.randomUUID());
        clockEvent.setTenantId(TENANT);
        clockEvent.setWorkerId(AMINA);

        shift = new Shift();
        shift.setId(UUID.randomUUID());
        shift.setTenantId(TENANT);
        shift.setWorkerId(AMINA);

        when(clockEvents.findAllByTenantIdAndWorkerIdOrderByOccurredAtDesc(TENANT, AMINA))
                .thenReturn(List.of(clockEvent));
        when(clockEvents.findAllByTenantIdAndWorkerIdOrderByOccurredAtDesc(TENANT, JOSEPH))
                .thenReturn(List.of());
        when(clockEvents.findByIdAndTenantIdAndWorkerId(clockEvent.getId(), TENANT, AMINA))
                .thenReturn(Optional.of(clockEvent));
        when(clockEvents.findByIdAndTenantIdAndWorkerId(clockEvent.getId(), TENANT, JOSEPH))
                .thenReturn(Optional.empty());

        when(shifts.findAllByTenantIdAndWorkerIdOrderByStartedAtDesc(TENANT, AMINA))
                .thenReturn(List.of(shift));
        when(shifts.findAllByTenantIdAndWorkerIdOrderByStartedAtDesc(TENANT, JOSEPH))
                .thenReturn(List.of());
        when(shifts.findByIdAndTenantIdAndWorkerId(shift.getId(), TENANT, AMINA))
                .thenReturn(Optional.of(shift));
        when(shifts.findByIdAndTenantIdAndWorkerId(shift.getId(), TENANT, JOSEPH))
                .thenReturn(Optional.empty());
    }

    @Test
    void aWorkerSeesOnlyTheirOwnClockEvents() {
        assertThat(service.myClockEvents(AMINA)).containsExactly(clockEvent);
        assertThat(service.myClockEvents(JOSEPH)).isEmpty();
    }

    @Test
    void aWorkerGetsNotFoundOnSomeoneElsesClockEvent() {
        assertThat(service.myClockEvent(clockEvent.getId(), AMINA)).isEqualTo(clockEvent);
        assertThatThrownBy(() -> service.myClockEvent(clockEvent.getId(), JOSEPH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aWorkerSeesOnlyTheirOwnShifts() {
        assertThat(service.myShifts(AMINA)).containsExactly(shift);
        assertThat(service.myShifts(JOSEPH)).isEmpty();
    }

    @Test
    void aWorkerGetsNotFoundOnSomeoneElsesShift() {
        assertThat(service.myShift(shift.getId(), AMINA)).isEqualTo(shift);
        assertThatThrownBy(() -> service.myShift(shift.getId(), JOSEPH))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
