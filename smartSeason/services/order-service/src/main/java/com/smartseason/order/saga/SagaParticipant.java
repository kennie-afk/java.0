package com.smartseason.order.saga;

import java.util.UUID;

public interface SagaParticipant {

    SagaStep step();

    StepResult execute(UUID orderId, SagaContext context);

    void compensate(UUID orderId, SagaContext context);
}
