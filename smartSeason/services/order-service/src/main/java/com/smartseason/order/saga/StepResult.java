package com.smartseason.order.saga;

public record StepResult(boolean success, String detail) {

    public static StepResult ok(String detail) {
        return new StepResult(true, detail);
    }

    public static StepResult failed(String detail) {
        return new StepResult(false, detail);
    }
}
