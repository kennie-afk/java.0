package com.smartseason.order.saga;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class AuthorisePaymentParticipant implements SagaParticipant {

    private final BiFunction<UUID, SagaContext, StepResult> authorise;
    private final BiConsumer<UUID, SagaContext> refund;

    public AuthorisePaymentParticipant(BiFunction<UUID, SagaContext, StepResult> authorise,
                                       BiConsumer<UUID, SagaContext> refund) {
        this.authorise = authorise;
        this.refund = refund;
    }

    @Override
    public SagaStep step() {
        return SagaStep.AUTHORISE_PAYMENT;
    }

    @Override
    public StepResult execute(UUID orderId, SagaContext context) {
        return authorise.apply(orderId, context);
    }

    @Override
    public void compensate(UUID orderId, SagaContext context) {
        refund.accept(orderId, context);
    }
}
