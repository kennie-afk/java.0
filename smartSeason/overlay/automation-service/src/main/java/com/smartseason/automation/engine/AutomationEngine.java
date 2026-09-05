package com.smartseason.automation.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AutomationEngine {

    private final Clock clock;

    public interface Clock {
        Instant now();
    }

    public AutomationEngine() {
        this(Instant::now);
    }

    public AutomationEngine(Clock clock) {
        this.clock = clock;
    }

    public List<Decision> evaluate(Reading reading,
                                   List<RuleDefinition> rules,
                                   TwinState twin,
                                   List<Interlock> interlocks) {
        List<Decision> decisions = new ArrayList<>();

        for (RuleDefinition rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            if (!rule.triggerMetric().equals(reading.metric())) {
                continue;
            }
            if (rule.plotId() != null && !rule.plotId().equals(reading.plotId())) {
                continue;
            }
            if (reading.value() == null || rule.threshold() == null) {
                continue;
            }
            if (!rule.operator().test(reading.value(), rule.threshold())) {
                continue;
            }

            String trigger = "%s %s %s %s".formatted(
                    reading.metric(), reading.value().toPlainString(),
                    rule.operator(), rule.threshold().toPlainString());

            if (withinCooldown(rule)) {
                decisions.add(Decision.suppressed(rule,
                        "%s but the rule is still within its %ds cooldown"
                                .formatted(trigger, rule.cooldownSeconds())));
                continue;
            }

            List<String> blocks = blockingInterlocks(rule, twin, interlocks);
            if (!blocks.isEmpty()) {
                decisions.add(Decision.blocked(rule, blocks));
                continue;
            }

            decisions.add(Decision.issue(rule, trigger));
        }

        return List.copyOf(decisions);
    }

    private boolean withinCooldown(RuleDefinition rule) {
        if (rule.lastTriggeredAt() == null || rule.cooldownSeconds() <= 0) {
            return false;
        }
        Duration since = Duration.between(rule.lastTriggeredAt(), clock.now());
        return since.getSeconds() < rule.cooldownSeconds();
    }

    private List<String> blockingInterlocks(RuleDefinition rule, TwinState twin,
                                            List<Interlock> interlocks) {
        List<String> blocks = new ArrayList<>();
        UUID target = rule.actionTargetDeviceId();

        for (Interlock interlock : interlocks) {
            if (!interlock.engaged()) {
                continue;
            }

            switch (interlock.type()) {
                case MANUAL_OVERRIDE -> {
                    if (matches(interlock.deviceId(), target)) {
                        blocks.add("manual override is engaged on the target device");
                    }
                }
                case MUTUAL_EXCLUSION -> {
                    if (matches(interlock.deviceId(), target)
                            && interlock.conflictingDeviceId() != null
                            && twin.deviceLastTriggered().containsKey(interlock.conflictingDeviceId())) {
                        blocks.add("a mutually exclusive device is currently active");
                    }
                }
                case MAX_RUNTIME -> {
                    if (matches(interlock.deviceId(), target)
                            && twin.isRunning()
                            && twin.runningSince() != null
                            && interlock.maxRuntimeSeconds() != null) {
                        long running = Duration.between(twin.runningSince(), clock.now()).getSeconds();
                        if (running >= interlock.maxRuntimeSeconds()) {
                            blocks.add("the device has already run for %ds, at or beyond its %ds ceiling"
                                    .formatted(running, interlock.maxRuntimeSeconds()));
                        }
                    }
                }
            }
        }

        return blocks;
    }

    private static boolean matches(UUID interlockDevice, UUID target) {
        return interlockDevice == null || interlockDevice.equals(target);
    }
}
