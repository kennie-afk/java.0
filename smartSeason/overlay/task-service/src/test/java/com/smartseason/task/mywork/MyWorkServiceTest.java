package com.smartseason.task.mywork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.task.domain.TaskAssignment;
import com.smartseason.task.platform.DomainRuleException;
import com.smartseason.task.platform.EventPublisher;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.MyWorkRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyWorkServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AMINA = UUID.randomUUID();
    private static final UUID JOSEPH = UUID.randomUUID();

    private MyWorkRepository repository;
    private EventPublisher events;
    private MyWorkService service;
    private TaskAssignment assignment;

    @BeforeEach
    void setUp() {
        repository = mock(MyWorkRepository.class);
        events = mock(EventPublisher.class);
        service = new MyWorkService(repository, events);
        TenantContext.set(TENANT);

        assignment = new TaskAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(TENANT);
        assignment.setWorkerUserId(AMINA);
        assignment.setStatus(TaskAssignment.Status.ASSIGNED);

        when(repository.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(repository.save(any(TaskAssignment.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void startingStampsTheServerClockNotTheCaller() {
        Instant before = Instant.now();
        TaskAssignment started = service.start(assignment.getId(), AMINA, "WORKER", false);

        assertThat(started.getStartedAt()).isNotNull();
        assertThat(started.getStartedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(started.getStatus()).isEqualTo(TaskAssignment.Status.IN_PROGRESS);
        // Starting implies accepting, so the timeline has no hole in it.
        assertThat(started.getAcceptedAt()).isNotNull();
    }

    @Test
    void startingPublishesADomainEventForAuditServiceToConsume() {
        service.start(assignment.getId(), AMINA, "WORKER", false);

        verify(events).publish(eq("task"), eq("TaskStarted"), eq(assignment.getId()), any(TaskWorkEvent.class));
    }

    @Test
    void stoppingPublishesADomainEventForAuditServiceToConsume() {
        service.start(assignment.getId(), AMINA, "WORKER", false);
        service.stop(assignment.getId(), AMINA, "WORKER", false);

        verify(events).publish(eq("task"), eq("TaskStopped"), eq(assignment.getId()), any(TaskWorkEvent.class));
    }

    @Test
    void aWorkerCannotStartSomeoneElsesTask() {
        assertThatThrownBy(() -> service.start(assignment.getId(), JOSEPH, "WORKER", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aWorkerCannotStopSomeoneElsesTask() {
        service.start(assignment.getId(), AMINA, "WORKER", false);

        assertThatThrownBy(() -> service.stop(assignment.getId(), JOSEPH, "WORKER", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aSupervisorMayActOnAnyAssignment() {
        TaskAssignment started = service.start(assignment.getId(), JOSEPH, "MANAGER", true);

        assertThat(started.getStatus()).isEqualTo(TaskAssignment.Status.IN_PROGRESS);
    }

    @Test
    void aTaskCannotBeStartedTwice() {
        service.start(assignment.getId(), AMINA, "WORKER", false);

        assertThatThrownBy(() -> service.start(assignment.getId(), AMINA, "WORKER", false))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void aTaskCannotBeStoppedBeforeItStarts() {
        assertThatThrownBy(() -> service.stop(assignment.getId(), AMINA, "WORKER", false))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("never started");
    }

    @Test
    void aFinishedTaskCannotBeReopenedOrStoppedAgain() {
        service.start(assignment.getId(), AMINA, "WORKER", false);
        service.stop(assignment.getId(), AMINA, "WORKER", false);

        assertThatThrownBy(() -> service.stop(assignment.getId(), AMINA, "WORKER", false))
                .isInstanceOf(DomainRuleException.class);
        assertThatThrownBy(() -> service.start(assignment.getId(), AMINA, "WORKER", false))
                .isInstanceOf(DomainRuleException.class);
    }

    @Test
    void anAssignmentFromAnotherTenantIsNotVisibleAtAll() {
        assignment.setTenantId(UUID.randomUUID());

        assertThatThrownBy(() -> service.start(assignment.getId(), AMINA, "WORKER", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
