package com.smartseason.fraud.ingest;

import com.smartseason.fraud.domain.FraudCase;
import com.smartseason.fraud.domain.FraudEvidence;
import com.smartseason.fraud.domain.FraudSignal;
import com.smartseason.fraud.domain.WorkerRiskScore;
import com.smartseason.fraud.engine.Detection;
import com.smartseason.fraud.engine.FraudRuleEngine;
import com.smartseason.fraud.engine.Severity;
import com.smartseason.fraud.engine.WorkerActivity;
import com.smartseason.fraud.platform.EventPublisher;
import com.smartseason.fraud.repo.FraudCaseRepository;
import com.smartseason.fraud.repo.FraudEvidenceRepository;
import com.smartseason.fraud.repo.FraudSignalRepository;
import com.smartseason.fraud.repo.WorkerRiskScoreRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudIngestService {

    private static final Logger log = LoggerFactory.getLogger(FraudIngestService.class);
    private static final double CASE_THRESHOLD = 0.60;
    private static final double PAYOUT_HOLD_THRESHOLD = 0.75;

    private final FraudRuleEngine engine;
    private final FraudSignalRepository signals;
    private final FraudCaseRepository cases;
    private final FraudEvidenceRepository evidence;
    private final WorkerRiskScoreRepository riskScores;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;

    public FraudIngestService(FraudSignalRepository signals,
                              FraudCaseRepository cases,
                              FraudEvidenceRepository evidence,
                              WorkerRiskScoreRepository riskScores,
                              EventPublisher events,
                              ObjectMapper objectMapper) {
        this.engine = FraudRuleEngine.withDefaults();
        this.signals = signals;
        this.cases = cases;
        this.evidence = evidence;
        this.riskScores = riskScores;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Detection> ingestClockEvent(UUID tenantId, ClockEventEnvelope.Payload payload) {
        if (payload == null || payload.workerId() == null) {
            log.debug("Ignoring clock event without a worker id");
            return List.of();
        }

        WorkerActivity activity = toActivity(payload);
        List<Detection> detections = engine.evaluate(activity);

        if (detections.isEmpty()) {
            return detections;
        }

        for (Detection detection : detections) {
            FraudCase opened = detection.confidence() >= CASE_THRESHOLD
                    ? openCase(tenantId, payload, detection)
                    : null;
            recordSignal(tenantId, payload, detection, opened);
        }

        updateRiskScore(tenantId, payload, detections);
        return detections;
    }

    private WorkerActivity toActivity(ClockEventEnvelope.Payload payload) {
        WorkerActivity.ClockPoint point = new WorkerActivity.ClockPoint(
                payload.occurredAt() == null ? Instant.now() : payload.occurredAt(),
                payload.latitude(),
                payload.longitude(),
                payload.accuracyM(),
                payload.biometricScore(),
                Boolean.TRUE.equals(payload.insideGeofence()),
                Boolean.TRUE.equals(payload.mockLocation()),
                payload.deviceId());

        return new WorkerActivity(
                payload.workerId(), payload.farmId(), true,
                point.occurredAt(), point.occurredAt(),
                List.of(point), List.of(), List.of(),
                null, null, null);
    }

    private FraudCase openCase(UUID tenantId, ClockEventEnvelope.Payload payload,
                               Detection detection) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.setTenantId(tenantId);
        fraudCase.setCaseNumber("FC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        fraudCase.setSubjectType(FraudCase.SubjectType.WORKER);
        fraudCase.setSubjectId(payload.workerId());
        fraudCase.setFarmId(payload.farmId());
        fraudCase.setTypology(detection.typology().name());
        fraudCase.setSeverity(FraudCase.Severity.valueOf(detection.severity().name()));
        fraudCase.setConfidence(BigDecimal.valueOf(detection.confidence()));
        fraudCase.setOpenedAt(Instant.now());
        fraudCase.setStatus(FraudCase.Status.OPEN);
        fraudCase.setPayoutHeld(detection.confidence() >= PAYOUT_HOLD_THRESHOLD);
        FraudCase saved = cases.save(fraudCase);

        FraudEvidence bundle = new FraudEvidence();
        bundle.setTenantId(tenantId);
        bundle.setCaseId(saved.getId());
        bundle.setLabel(detection.ruleCode());
        bundle.setEvidenceType("CLOCK_EVENT");
        bundle.setPayload(asJson(detection.evidence()));
        bundle.setWeight(BigDecimal.valueOf(detection.confidence()));
        bundle.setCollectedAt(Instant.now());
        evidence.save(bundle);

        events.publish("workforce", "FraudCaseOpened", saved.getId(), saved.getCaseNumber());
        if (Boolean.TRUE.equals(saved.getPayoutHeld())) {
            events.publish("workforce", "PayoutHoldRequested", saved.getSubjectId(),
                    saved.getCaseNumber());
        }

        log.info("Opened fraud case {} for worker {} ({}, confidence {})",
                saved.getCaseNumber(), payload.workerId(), detection.typology(),
                detection.confidence());
        return saved;
    }

    private void recordSignal(UUID tenantId, ClockEventEnvelope.Payload payload,
                              Detection detection, FraudCase opened) {
        FraudSignal signal = new FraudSignal();
        signal.setTenantId(tenantId);
        signal.setSubjectType(FraudSignal.SubjectType.WORKER);
        signal.setSubjectId(payload.workerId());
        signal.setRuleCode(detection.ruleCode());
        signal.setTypology(detection.typology().name());
        signal.setScore(BigDecimal.valueOf(detection.confidence()));
        signal.setDetectedAt(Instant.now());
        signal.setSourceEvent("ClockEventCreated");
        signal.setDetails(asJson(detection.evidence()));
        signal.setCaseId(opened == null ? null : opened.getId());
        signals.save(signal);
    }

    private void updateRiskScore(UUID tenantId, ClockEventEnvelope.Payload payload,
                                 List<Detection> detections) {
        WorkerRiskScore score = riskScores
                .findByWorkerIdAndTenantId(payload.workerId(), tenantId)
                .orElseGet(() -> {
                    WorkerRiskScore fresh = new WorkerRiskScore();
                    fresh.setTenantId(tenantId);
                    fresh.setWorkerId(payload.workerId());
                    fresh.setFarmId(payload.farmId());
                    fresh.setScore(0);
                    fresh.setOpenCases(0);
                    return fresh;
                });

        int computed = engine.riskScore(detections);
        int updated = Math.min(100, Math.max(score.getScore(), computed));

        score.setScore(updated);
        score.setBand(WorkerRiskScore.Band.valueOf(Severity.fromConfidence(updated / 100.0).name()));
        score.setOpenCases(score.getOpenCases()
                + (int) detections.stream().filter(d -> d.confidence() >= CASE_THRESHOLD).count());
        score.setLastSignalAt(Instant.now());
        riskScores.save(score);

        events.publish("workforce", "WorkerRiskScoreUpdated", payload.workerId(), updated);
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise evidence payload", ex);
            return "{}";
        }
    }
}
