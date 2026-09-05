package com.smartseason.fraud.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.fraud.domain.FraudCase;
import com.smartseason.fraud.domain.FraudEvidence;
import com.smartseason.fraud.domain.FraudSignal;
import com.smartseason.fraud.domain.WorkerRiskScore;
import com.smartseason.fraud.engine.Detection;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.platform.TenantContext;
import com.smartseason.fraud.repo.FraudCaseRepository;
import com.smartseason.fraud.repo.FraudEvidenceRepository;
import com.smartseason.fraud.repo.FraudSignalRepository;
import com.smartseason.fraud.repo.WorkerRiskScoreRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FraudIngestServiceTest {

    private final FraudSignalRepository signals = mock(FraudSignalRepository.class);
    private final FraudCaseRepository cases = mock(FraudCaseRepository.class);
    private final FraudEvidenceRepository evidence = mock(FraudEvidenceRepository.class);
    private final WorkerRiskScoreRepository riskScores = mock(WorkerRiskScoreRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final FraudIngestService service = new FraudIngestService(
            signals, cases, evidence, riskScores, events, new ObjectMapper());

    private final UUID tenant = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final UUID farm = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);
        when(cases.save(any(FraudCase.class))).thenAnswer(invocation -> {
            FraudCase value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(UUID.randomUUID());
            }
            return value;
        });
        when(signals.save(any(FraudSignal.class))).thenAnswer(i -> i.getArgument(0));
        when(evidence.save(any(FraudEvidence.class))).thenAnswer(i -> i.getArgument(0));
        when(riskScores.save(any(WorkerRiskScore.class))).thenAnswer(i -> i.getArgument(0));
        when(riskScores.findByWorkerIdAndTenantId(worker, tenant)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ClockEventEnvelope.Payload payload(Double biometric, boolean inside, boolean mocked) {
        return new ClockEventEnvelope.Payload(
                UUID.randomUUID(), worker, farm, "CLOCK_IN", Instant.now(),
                -1.2921, 36.8219, 8.0, biometric, inside, mocked, "device-1");
    }

    @Test
    @DisplayName("a clean clock event produces no signal, no case and no risk update")
    void cleanEventIsIgnored() {
        List<Detection> detections = service.ingestClockEvent(tenant, payload(0.96, true, false));

        assertThat(detections).isEmpty();
        verify(signals, never()).save(any());
        verify(cases, never()).save(any());
        verify(riskScores, never()).save(any());
    }

    @Test
    @DisplayName("a mocked location opens a case, stores evidence and holds the payout")
    void mockedLocationOpensCase() {
        List<Detection> detections = service.ingestClockEvent(tenant, payload(0.35, false, true));

        assertThat(detections).isNotEmpty();

        ArgumentCaptor<FraudCase> caseCaptor = ArgumentCaptor.forClass(FraudCase.class);
        verify(cases).save(caseCaptor.capture());
        FraudCase opened = caseCaptor.getValue();

        assertThat(opened.getSubjectId()).isEqualTo(worker);
        assertThat(opened.getTenantId()).isEqualTo(tenant);
        assertThat(opened.getStatus()).isEqualTo(FraudCase.Status.OPEN);
        assertThat(opened.getPayoutHeld())
                .as("high confidence must hold the payout pending review")
                .isTrue();
        assertThat(opened.getCaseNumber()).startsWith("FC-");

        verify(evidence).save(any(FraudEvidence.class));
        verify(events).publish(eq("workforce"), eq("FraudCaseOpened"), any(), any());
        verify(events).publish(eq("workforce"), eq("PayoutHoldRequested"), any(), any());
    }

    @Test
    @DisplayName("the signal is linked to the case it opened")
    void signalLinksToCase() {
        service.ingestClockEvent(tenant, payload(0.35, false, true));

        ArgumentCaptor<FraudSignal> captor = ArgumentCaptor.forClass(FraudSignal.class);
        verify(signals).save(captor.capture());

        assertThat(captor.getValue().getCaseId()).isNotNull();
        assertThat(captor.getValue().getSourceEvent()).isEqualTo("ClockEventCreated");
        assertThat(captor.getValue().getDetails()).contains("mockLocation");
    }

    @Test
    @DisplayName("the worker risk score is created and banded from the detections")
    void riskScoreIsUpdated() {
        service.ingestClockEvent(tenant, payload(0.35, false, true));

        ArgumentCaptor<WorkerRiskScore> captor = ArgumentCaptor.forClass(WorkerRiskScore.class);
        verify(riskScores).save(captor.capture());
        WorkerRiskScore score = captor.getValue();

        assertThat(score.getWorkerId()).isEqualTo(worker);
        assertThat(score.getScore()).isBetween(1, 100);
        assertThat(score.getBand()).isNotNull();
        assertThat(score.getOpenCases()).isPositive();

        verify(events).publish(eq("workforce"), eq("WorkerRiskScoreUpdated"), eq(worker), any());
    }

    @Test
    @DisplayName("an event without a worker id is discarded rather than throwing")
    void missingWorkerIsDiscarded() {
        ClockEventEnvelope.Payload broken = new ClockEventEnvelope.Payload(
                UUID.randomUUID(), null, farm, "CLOCK_IN", Instant.now(),
                null, null, null, null, null, null, null);

        assertThat(service.ingestClockEvent(tenant, broken)).isEmpty();
        verify(signals, never()).save(any());
    }

    @Test
    @DisplayName("a null payload is discarded rather than throwing")
    void nullPayloadIsDiscarded() {
        assertThat(service.ingestClockEvent(tenant, null)).isEmpty();
        verify(cases, never()).save(any());
    }
}
