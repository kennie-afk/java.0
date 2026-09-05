package com.smartseason.automation.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {

    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");

    private final UUID plot = UUID.randomUUID();
    private final UUID valve = UUID.randomUUID();
    private final UUID pump = UUID.randomUUID();

    private final AutomationEngine engine = new AutomationEngine(() -> NOW);

    private Reading moisture(String value) {
        return new Reading(UUID.randomUUID(), plot, "soil_moisture_pct",
                new BigDecimal(value), "%", NOW);
    }

    private RuleDefinition irrigateBelow(String threshold, int cooldown, Instant lastTriggered) {
        return new RuleDefinition(UUID.randomUUID(), "Irrigate when dry", plot,
                "soil_moisture_pct", RuleDefinition.Operator.LT, new BigDecimal(threshold),
                "IRRIGATE", valve, 900, cooldown, true, lastTriggered);
    }

    @Test
    @DisplayName("dry soil below the threshold issues an irrigation command")
    void drySoilTriggersIrrigation() {
        List<Decision> decisions = engine.evaluate(moisture("18"),
                List.of(irrigateBelow("25", 3600, null)),
                TwinState.idle(plot), List.of());

        assertThat(decisions).hasSize(1);
        Decision decision = decisions.getFirst();
        assertThat(decision.willAct()).isTrue();
        assertThat(decision.targetDeviceId()).isEqualTo(valve);
        assertThat(decision.durationSeconds()).isEqualTo(900);
        assertThat(decision.explanation()).contains("soil_moisture_pct 18 LT 25");
    }

    @Test
    @DisplayName("soil above the threshold does nothing at all")
    void wetSoilDoesNothing() {
        assertThat(engine.evaluate(moisture("42"),
                List.of(irrigateBelow("25", 3600, null)),
                TwinState.idle(plot), List.of())).isEmpty();
    }

    @Test
    @DisplayName("a disabled rule never fires")
    void disabledRuleNeverFires() {
        RuleDefinition disabled = new RuleDefinition(UUID.randomUUID(), "Off", plot,
                "soil_moisture_pct", RuleDefinition.Operator.LT, new BigDecimal("25"),
                "IRRIGATE", valve, 900, 0, false, null);

        assertThat(engine.evaluate(moisture("10"), List.of(disabled),
                TwinState.idle(plot), List.of())).isEmpty();
    }

    @Test
    @DisplayName("a rule for another metric or another plot is not considered")
    void unrelatedRulesIgnored() {
        RuleDefinition otherMetric = new RuleDefinition(UUID.randomUUID(), "Temp", plot,
                "air_temp_c", RuleDefinition.Operator.GT, new BigDecimal("30"),
                "VENTILATE", valve, 60, 0, true, null);
        RuleDefinition otherPlot = new RuleDefinition(UUID.randomUUID(), "Other plot",
                UUID.randomUUID(), "soil_moisture_pct", RuleDefinition.Operator.LT,
                new BigDecimal("25"), "IRRIGATE", valve, 900, 0, true, null);

        assertThat(engine.evaluate(moisture("10"), List.of(otherMetric, otherPlot),
                TwinState.idle(plot), List.of())).isEmpty();
    }

    @Nested
    class Cooldown {

        @Test
        @DisplayName("a rule that fired recently is suppressed rather than re-firing")
        void recentTriggerIsSuppressed() {
            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 3600, NOW.minus(Duration.ofMinutes(10)))),
                    TwinState.idle(plot), List.of());

            assertThat(decisions).hasSize(1);
            assertThat(decisions.getFirst().action()).isEqualTo(Decision.Action.SUPPRESSED);
            assertThat(decisions.getFirst().explanation()).contains("cooldown");
        }

        @Test
        @DisplayName("once the cooldown has elapsed the rule fires again")
        void elapsedCooldownFiresAgain() {
            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 3600, NOW.minus(Duration.ofHours(2)))),
                    TwinState.idle(plot), List.of());

            assertThat(decisions.getFirst().willAct()).isTrue();
        }
    }

    @Nested
    class SafetyInterlocks {

        @Test
        @DisplayName("a manual override blocks the command, so a person at the valve wins")
        void manualOverrideBlocks() {
            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)),
                    TwinState.idle(plot),
                    List.of(new Interlock(valve, Interlock.Type.MANUAL_OVERRIDE, null, null, true)));

            Decision decision = decisions.getFirst();
            assertThat(decision.action()).isEqualTo(Decision.Action.BLOCKED);
            assertThat(decision.blockedBy()).anyMatch(r -> r.contains("manual override"));
        }

        @Test
        @DisplayName("a device already at its runtime ceiling is not told to run again")
        void maxRuntimeBlocks() {
            TwinState running = new TwinState(plot, "RUNNING", null,
                    NOW.minus(Duration.ofMinutes(40)), Map.of());

            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)), running,
                    List.of(new Interlock(valve, Interlock.Type.MAX_RUNTIME, 1800, null, true)));

            assertThat(decisions.getFirst().action()).isEqualTo(Decision.Action.BLOCKED);
            assertThat(decisions.getFirst().blockedBy())
                    .anyMatch(r -> r.contains("ceiling"));
        }

        @Test
        @DisplayName("a mutually exclusive device that is active blocks the command")
        void mutualExclusionBlocks() {
            TwinState withPumpActive = new TwinState(plot, "IDLE", null, null,
                    Map.of(pump, NOW.minus(Duration.ofMinutes(1))));

            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)), withPumpActive,
                    List.of(new Interlock(valve, Interlock.Type.MUTUAL_EXCLUSION, null, pump, true)));

            assertThat(decisions.getFirst().action()).isEqualTo(Decision.Action.BLOCKED);
            assertThat(decisions.getFirst().blockedBy())
                    .anyMatch(r -> r.contains("mutually exclusive"));
        }

        @Test
        @DisplayName("a disengaged interlock does not block anything")
        void disengagedInterlockDoesNotBlock() {
            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)), TwinState.idle(plot),
                    List.of(new Interlock(valve, Interlock.Type.MANUAL_OVERRIDE, null, null, false)));

            assertThat(decisions.getFirst().willAct()).isTrue();
        }

        @Test
        @DisplayName("an interlock on a different device leaves this one alone")
        void interlockOnOtherDeviceIgnored() {
            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)), TwinState.idle(plot),
                    List.of(new Interlock(pump, Interlock.Type.MANUAL_OVERRIDE, null, null, true)));

            assertThat(decisions.getFirst().willAct()).isTrue();
        }

        @Test
        @DisplayName("every blocking reason is reported, not just the first")
        void allBlockingReasonsReported() {
            TwinState running = new TwinState(plot, "RUNNING", null,
                    NOW.minus(Duration.ofMinutes(40)), Map.of());

            List<Decision> decisions = engine.evaluate(moisture("18"),
                    List.of(irrigateBelow("25", 0, null)), running,
                    List.of(new Interlock(valve, Interlock.Type.MANUAL_OVERRIDE, null, null, true),
                            new Interlock(valve, Interlock.Type.MAX_RUNTIME, 1800, null, true)));

            assertThat(decisions.getFirst().blockedBy()).hasSize(2);
        }
    }

    @Test
    @DisplayName("the operators compare as their names say")
    void operatorSemantics() {
        BigDecimal ten = new BigDecimal("10");
        assertThat(RuleDefinition.Operator.LT.test(new BigDecimal("9"), ten)).isTrue();
        assertThat(RuleDefinition.Operator.LTE.test(ten, ten)).isTrue();
        assertThat(RuleDefinition.Operator.GT.test(new BigDecimal("11"), ten)).isTrue();
        assertThat(RuleDefinition.Operator.GTE.test(ten, ten)).isTrue();
        assertThat(RuleDefinition.Operator.EQ.test(new BigDecimal("10.0"), ten)).isTrue();
        assertThat(RuleDefinition.Operator.LT.test(ten, ten)).isFalse();
    }

    @Test
    @DisplayName("several matching rules each produce their own decision")
    void multipleRulesEachDecide() {
        List<Decision> decisions = engine.evaluate(moisture("18"),
                List.of(irrigateBelow("25", 0, null), irrigateBelow("20", 0, null)),
                TwinState.idle(plot), List.of());

        assertThat(decisions).hasSize(2).allMatch(Decision::willAct);
    }
}
