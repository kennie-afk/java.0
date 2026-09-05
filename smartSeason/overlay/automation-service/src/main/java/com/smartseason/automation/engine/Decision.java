package com.smartseason.automation.engine;

import java.util.List;
import java.util.UUID;

public record Decision(
        UUID ruleId,
        String ruleName,
        Action action,
        UUID targetDeviceId,
        Integer durationSeconds,
        String explanation,
        List<String> blockedBy) {

    public enum Action { ISSUE_COMMAND, SUPPRESSED, BLOCKED }

    public boolean willAct() {
        return action == Action.ISSUE_COMMAND;
    }

    public static Decision issue(RuleDefinition rule, String explanation) {
        return new Decision(rule.id(), rule.name(), Action.ISSUE_COMMAND,
                rule.actionTargetDeviceId(), rule.durationSeconds(), explanation, List.of());
    }

    public static Decision suppressed(RuleDefinition rule, String explanation) {
        return new Decision(rule.id(), rule.name(), Action.SUPPRESSED,
                rule.actionTargetDeviceId(), null, explanation, List.of());
    }

    public static Decision blocked(RuleDefinition rule, List<String> reasons) {
        return new Decision(rule.id(), rule.name(), Action.BLOCKED,
                rule.actionTargetDeviceId(), null,
                "blocked by a safety interlock", List.copyOf(reasons));
    }
}
