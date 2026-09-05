package com.smartseason.order.saga;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ConfirmOrderParticipant implements SagaParticipant {

    private final BiFunction<UUID, SagaContext, StepResult> confirm;
    private final BiConsumer<UUID, SagaContext> cancel;

    public ConfirmOrderParticipant(BiFunction<UUID, SagaContext, StepResult> confirm,
                                   BiConsumer<UUID, SagaContext> cancel) {
        this.confirm = confirm;
        this.cancel = cancel;
    }

    @Override
    public SagaStep step() {
        return SagaStep.CONFIRM_ORDER;
    }

    @Override
    public StepResult execute(UUID orderId, SagaContext context) {
        return confirm.apply(orderId, context);
    }

    @Override
    public void compensate(UUID orderId, SagaContext context) {
        cancel.accept(orderId, context);
    }
}
