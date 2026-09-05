package com.smartseason.order.saga;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final Map<SagaStep, SagaParticipant> participants = new LinkedHashMap<>();

    public OrderSagaOrchestrator(List<SagaParticipant> participants) {
        for (SagaParticipant participant : participants) {
            this.participants.put(participant.step(), participant);
        }
    }

    public SagaOutcome run(UUID orderId, SagaContext context) {
        List<String> journal = new ArrayList<>();
        Deque<SagaParticipant> completed = new ArrayDeque<>();

        SagaStep step = SagaStep.RESERVE_STOCK;
        while (!step.isTerminal()) {
            SagaParticipant participant = participants.get(step);
            if (participant == null) {
                journal.add(step + ": no participant registered, skipped");
                step = step.next();
                continue;
            }

            StepResult result;
            try {
                result = participant.execute(orderId, context);
            } catch (RuntimeException ex) {
                log.error("Saga step {} threw for order {}", step, orderId, ex);
                result = StepResult.failed(ex.getMessage() == null
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage());
            }

            if (!result.success()) {
                journal.add(step + ": FAILED - " + result.detail());
                compensate(orderId, context, completed, journal);
                return SagaOutcome.failed(step, result.detail(), journal);
            }

            journal.add(step + ": " + result.detail());
            completed.push(participant);
            step = step.next();
        }

        journal.add("COMPLETE");
        return SagaOutcome.completed(journal);
    }

    private void compensate(UUID orderId, SagaContext context,
                            Deque<SagaParticipant> completed, List<String> journal) {
        while (!completed.isEmpty()) {
            SagaParticipant participant = completed.pop();
            try {
                participant.compensate(orderId, context);
                journal.add(participant.step() + ": compensated");
            } catch (RuntimeException ex) {
                log.error("Compensation for {} failed on order {}; continuing so the remaining "
                        + "steps are still unwound", participant.step(), orderId, ex);
                journal.add(participant.step() + ": COMPENSATION FAILED - " + ex.getMessage());
            }
        }
    }
}
