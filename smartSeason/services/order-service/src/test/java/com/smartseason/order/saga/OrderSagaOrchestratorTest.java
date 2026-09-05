package com.smartseason.order.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderSagaOrchestratorTest {

    private final UUID orderId = UUID.randomUUID();
    private final List<String> calls = new ArrayList<>();

    private SagaContext context() {
        return new SagaContext(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5000.00"), "KES", "idem-1");
    }

    private SagaParticipant participant(SagaStep step, boolean succeeds) {
        return new SagaParticipant() {
            @Override
            public SagaStep step() {
                return step;
            }

            @Override
            public StepResult execute(UUID id, SagaContext ctx) {
                calls.add("do:" + step);
                return succeeds ? StepResult.ok("done") : StepResult.failed("upstream refused");
            }

            @Override
            public void compensate(UUID id, SagaContext ctx) {
                calls.add("undo:" + step);
            }
        };
    }

    private SagaParticipant throwing(SagaStep step) {
        return new SagaParticipant() {
            @Override
            public SagaStep step() {
                return step;
            }

            @Override
            public StepResult execute(UUID id, SagaContext ctx) {
                calls.add("do:" + step);
                throw new IllegalStateException("connection reset");
            }

            @Override
            public void compensate(UUID id, SagaContext ctx) {
                calls.add("undo:" + step);
            }
        };
    }

    private OrderSagaOrchestrator orchestrator(SagaParticipant... participants) {
        return new OrderSagaOrchestrator(List.of(participants));
    }

    @Test
    @DisplayName("a saga where every step succeeds completes and compensates nothing")
    void happyPath() {
        var saga = orchestrator(
                participant(SagaStep.RESERVE_STOCK, true),
                participant(SagaStep.AUTHORISE_PAYMENT, true),
                participant(SagaStep.CONFIRM_ORDER, true),
                participant(SagaStep.ARRANGE_TRANSPORT, true));

        SagaOutcome outcome = saga.run(orderId, context());

        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.compensated()).isFalse();
        assertThat(outcome.reachedStep()).isEqualTo(SagaStep.COMPLETE);
        assertThat(calls).containsExactly(
                "do:RESERVE_STOCK", "do:AUTHORISE_PAYMENT",
                "do:CONFIRM_ORDER", "do:ARRANGE_TRANSPORT");
    }

    @Nested
    class Compensation {

        @Test
        @DisplayName("a payment failure releases the stock that was already reserved")
        void paymentFailureReleasesStock() {
            var saga = orchestrator(
                    participant(SagaStep.RESERVE_STOCK, true),
                    participant(SagaStep.AUTHORISE_PAYMENT, false),
                    participant(SagaStep.CONFIRM_ORDER, true));

            SagaOutcome outcome = saga.run(orderId, context());

            assertThat(outcome.completed()).isFalse();
            assertThat(outcome.compensated()).isTrue();
            assertThat(outcome.reachedStep()).isEqualTo(SagaStep.AUTHORISE_PAYMENT);
            assertThat(outcome.failureReason()).isEqualTo("upstream refused");
            assertThat(calls).containsExactly(
                    "do:RESERVE_STOCK", "do:AUTHORISE_PAYMENT", "undo:RESERVE_STOCK");
        }

        @Test
        @DisplayName("compensation unwinds in reverse order, so money is refunded before stock is released")
        void compensationRunsInReverse() {
            var saga = orchestrator(
                    participant(SagaStep.RESERVE_STOCK, true),
                    participant(SagaStep.AUTHORISE_PAYMENT, true),
                    participant(SagaStep.CONFIRM_ORDER, true),
                    participant(SagaStep.ARRANGE_TRANSPORT, false));

            saga.run(orderId, context());

            assertThat(calls).containsExactly(
                    "do:RESERVE_STOCK", "do:AUTHORISE_PAYMENT", "do:CONFIRM_ORDER",
                    "do:ARRANGE_TRANSPORT",
                    "undo:CONFIRM_ORDER", "undo:AUTHORISE_PAYMENT", "undo:RESERVE_STOCK");
        }

        @Test
        @DisplayName("a failure on the first step compensates nothing, because nothing succeeded")
        void firstStepFailureCompensatesNothing() {
            var saga = orchestrator(
                    participant(SagaStep.RESERVE_STOCK, false),
                    participant(SagaStep.AUTHORISE_PAYMENT, true));

            SagaOutcome outcome = saga.run(orderId, context());

            assertThat(outcome.compensated()).isTrue();
            assertThat(calls).containsExactly("do:RESERVE_STOCK");
        }

        @Test
        @DisplayName("a thrown exception is treated as a step failure, not a crash")
        void thrownExceptionBecomesFailure() {
            var saga = orchestrator(
                    participant(SagaStep.RESERVE_STOCK, true),
                    throwing(SagaStep.AUTHORISE_PAYMENT));

            SagaOutcome outcome = saga.run(orderId, context());

            assertThat(outcome.completed()).isFalse();
            assertThat(outcome.failureReason()).isEqualTo("connection reset");
            assertThat(calls).contains("undo:RESERVE_STOCK");
        }

        @Test
        @DisplayName("a compensation that itself fails does not stop the remaining unwinding")
        void compensationFailureDoesNotStopUnwinding() {
            SagaParticipant brokenCompensation = new SagaParticipant() {
                @Override
                public SagaStep step() {
                    return SagaStep.AUTHORISE_PAYMENT;
                }

                @Override
                public StepResult execute(UUID id, SagaContext ctx) {
                    calls.add("do:AUTHORISE_PAYMENT");
                    return StepResult.ok("authorised");
                }

                @Override
                public void compensate(UUID id, SagaContext ctx) {
                    calls.add("undo:AUTHORISE_PAYMENT(throws)");
                    throw new IllegalStateException("refund endpoint down");
                }
            };

            var saga = orchestrator(
                    participant(SagaStep.RESERVE_STOCK, true),
                    brokenCompensation,
                    participant(SagaStep.CONFIRM_ORDER, false));

            SagaOutcome outcome = saga.run(orderId, context());

            assertThat(outcome.compensated()).isTrue();
            assertThat(calls)
                    .as("stock must still be released even though the refund threw")
                    .containsSubsequence("undo:AUTHORISE_PAYMENT(throws)", "undo:RESERVE_STOCK");
            assertThat(outcome.log())
                    .anyMatch(line -> line.contains("COMPENSATION FAILED"));
        }
    }

    @Test
    @DisplayName("a step with no registered participant is skipped rather than blocking the saga")
    void missingParticipantIsSkipped() {
        var saga = orchestrator(
                participant(SagaStep.RESERVE_STOCK, true),
                participant(SagaStep.CONFIRM_ORDER, true));

        SagaOutcome outcome = saga.run(orderId, context());

        assertThat(outcome.completed()).isTrue();
        assertThat(outcome.log()).anyMatch(line -> line.contains("no participant registered"));
    }

    @Test
    @DisplayName("the journal records every step in order for audit")
    void journalRecordsEveryStep() {
        var saga = orchestrator(
                participant(SagaStep.RESERVE_STOCK, true),
                participant(SagaStep.AUTHORISE_PAYMENT, false));

        SagaOutcome outcome = saga.run(orderId, context());

        assertThat(outcome.log()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(outcome.log().get(0)).startsWith("RESERVE_STOCK");
        assertThat(outcome.log()).anyMatch(line -> line.contains("FAILED"));
    }

    @Test
    @DisplayName("the step sequence is fixed and terminates")
    void stepSequenceTerminates() {
        SagaStep step = SagaStep.RESERVE_STOCK;
        int guard = 0;
        while (!step.isTerminal() && guard++ < 10) {
            step = step.next();
        }
        assertThat(step).isEqualTo(SagaStep.COMPLETE);
        assertThat(guard).isEqualTo(4);
    }
}
