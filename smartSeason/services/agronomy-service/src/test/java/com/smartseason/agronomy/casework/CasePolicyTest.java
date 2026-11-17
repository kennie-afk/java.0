package com.smartseason.agronomy.casework;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartseason.agronomy.domain.Advisory.Severity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasePolicyTest {

    private static AgronomistProfile profile(String counties, int max, boolean active) {
        AgronomistProfile p = new AgronomistProfile();
        p.setUserId(UUID.randomUUID());
        p.setCounties(counties);
        p.setMaxOpen(max);
        p.setActive(active);
        return p;
    }

    @Test
    void priorityFollowsSeverityButAUserCannotClaimTheTopLevel() {
        assertThat(CasePolicy.priorityFor(Severity.HIGH, AdvisoryCase.Reason.REQUESTED)).isEqualTo(3);
        assertThat(CasePolicy.priorityFor(Severity.CRITICAL, AdvisoryCase.Reason.REQUESTED)).isEqualTo(3);
        assertThat(CasePolicy.priorityFor(Severity.LOW, AdvisoryCase.Reason.REQUESTED)).isEqualTo(1);
        assertThat(CasePolicy.priorityFor(null, AdvisoryCase.Reason.REQUESTED)).isEqualTo(2);
    }

    @Test
    void anUnsureAiLiftsALowSeverityCase() {
        assertThat(CasePolicy.priorityFor(Severity.LOW, AdvisoryCase.Reason.LOW_CONFIDENCE)).isEqualTo(2);
        assertThat(CasePolicy.priorityFor(Severity.INFO, AdvisoryCase.Reason.LOW_CONFIDENCE)).isEqualTo(2);
    }

    @Test
    void urgentCasesHaveShorterDeadlines() {
        assertThat(CasePolicy.answerWithin(3)).isLessThan(CasePolicy.answerWithin(2));
        assertThat(CasePolicy.answerWithin(2)).isLessThan(CasePolicy.answerWithin(1));
        assertThat(CasePolicy.answerWithin(3)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void routesToTheLeastLoadedAgronomistWhoCoversTheCounty() {
        AgronomistProfile busy = profile("nakuru", 25, true);
        AgronomistProfile free = profile("nakuru", 25, true);
        AgronomistProfile elsewhere = profile("kisumu", 25, true);
        Map<UUID, Long> load = Map.of(busy.getUserId(), 9L, free.getUserId(), 2L, elsewhere.getUserId(), 0L);

        var picked = CasePolicy.pick(List.of(busy, free, elsewhere), load, "Nakuru");
        assertThat(picked).containsSame(free);
    }

    @Test
    void aSpecialistBeatsAGeneralistEvenWhenTheGeneralistIsLessBusy() {
        AgronomistProfile specialist = profile("nakuru", 25, true);
        AgronomistProfile generalist = profile("", 25, true);
        Map<UUID, Long> load = Map.of(specialist.getUserId(), 10L, generalist.getUserId(), 0L);
        assertThat(CasePolicy.pick(List.of(generalist, specialist), load, "nakuru")).containsSame(specialist);
        // For a county nobody specialises in, the generalist takes it.
        assertThat(CasePolicy.pick(List.of(generalist, specialist), load, "turkana")).containsSame(generalist);
    }

    @Test
    void nobodyIsGivenCasesBeyondTheirLimitOrWhileInactive() {
        AgronomistProfile full = profile("", 2, true);
        AgronomistProfile away = profile("", 25, false);
        Map<UUID, Long> load = Map.of(full.getUserId(), 2L);
        assertThat(CasePolicy.pick(List.of(full, away), load, "nakuru")).isEmpty();
    }

    @Test
    void countyMatchingIgnoresCaseAndSpaces() {
        AgronomistProfile p = profile("nakuru,uasin gishu", 10, true);
        assertThat(p.covers(" Uasin Gishu ")).isTrue();
        assertThat(p.covers(null)).isFalse();
        assertThat(profile("", 10, true).covers(null)).isTrue();
    }

    @Test
    void cursorsRoundTripAndBadOnesStartFromTheTop() {
        CaseCursor c = new CaseCursor(3, java.time.Instant.parse("2026-11-17T10:15:30.123456Z"), UUID.randomUUID());
        assertThat(CaseCursor.decode(c.encode())).isEqualTo(c);
        assertThat(CaseCursor.decode("not-a-cursor")).isNull();
        assertThat(CaseCursor.decode(null)).isNull();
    }

    @Test
    void mailSubjectsCannotCarryHeaderInjection() {
        assertThat(CaseMailer.clean("Hello\r\nBcc: someone@else.com")).doesNotContain("\n").doesNotContain("\r");
    }
}
