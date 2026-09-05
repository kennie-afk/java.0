package com.smartseason.order.saga;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class ArrangeTransportParticipant implements SagaParticipant {

    private final BiFunction<UUID, SagaContext, StepResult> arrange;
    private final BiConsumer<UUID, SagaContext> cancel;

    public ArrangeTransportParticipant(BiFunction<UUID, SagaContext, StepResult> arrange,
                                       BiConsumer<UUID, SagaContext> cancel) {
        this.arrange = arrange;
        this.cancel = cancel;
    }

    @Override
    public SagaStep step() {
        return SagaStep.ARRANGE_TRANSPORT;
    }

    @Override
    public StepResult execute(UUID orderId, SagaContext context) {
        return arrange.apply(orderId, context);
    }

    @Override
    public void compensate(UUID orderId, SagaContext context) {
        cancel.accept(orderId, context);
    }
}
