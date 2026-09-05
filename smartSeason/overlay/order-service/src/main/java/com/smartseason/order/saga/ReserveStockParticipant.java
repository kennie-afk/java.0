package com.smartseason.order.saga;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;

public class ReserveStockParticipant implements SagaParticipant {

    private final BiFunction<UUID, SagaContext, StepResult> reserve;
    private final BiConsumer<UUID, SagaContext> release;

    public ReserveStockParticipant(BiFunction<UUID, SagaContext, StepResult> reserve,
                                   BiConsumer<UUID, SagaContext> release) {
        this.reserve = reserve;
        this.release = release;
    }

    @Override
    public SagaStep step() {
        return SagaStep.RESERVE_STOCK;
    }

    @Override
    public StepResult execute(UUID orderId, SagaContext context) {
        return reserve.apply(orderId, context);
    }

    @Override
    public void compensate(UUID orderId, SagaContext context) {
        release.accept(orderId, context);
    }
}
