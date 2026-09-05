package com.smartseason.payout.disburse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BatchPlannerTest {

    private final BatchPlanner planner = new BatchPlanner(PayoutPolicy.defaults());

    private PayeeSnapshot worker(String amount) {
        return new PayeeSnapshot(UUID.randomUUID(), "Amina Wanjiru", "254712345678",
                new BigDecimal(amount), true, false, false, false);
    }

    private PayeeSnapshot with(PayeeSnapshot base, boolean kyc, boolean fraud,
                               boolean dispute, boolean sanctioned) {
        return new PayeeSnapshot(base.payeeId(), base.payeeName(), base.payeePhone(),
                base.amount(), kyc, fraud, dispute, sanctioned);
    }

    @Test
    @DisplayName("a clean payee is paid")
    void cleanPayeeIsPayable() {
        BatchPlan plan = planner.plan(List.of(worker("4500")));

        assertThat(plan.payableCount()).isEqualTo(1);
        assertThat(plan.blockedCount()).isZero();
        assertThat(plan.payableTotal()).isEqualByComparingTo("4500");
    }

    @Nested
    class Holds {

        @Test
        @DisplayName("an open fraud case holds the payout, which is the whole point of the fraud engine")
        void openFraudCaseHolds() {
            BatchPlan plan = planner.plan(
                    List.of(with(worker("4500"), true, true, false, false)));

            assertThat(plan.blockedCount()).isEqualTo(1);
            assertThat(plan.blocked().getFirst().eligibility().summary())
                    .contains("open fraud case");
        }

        @Test
        @DisplayName("a sanctioned payee is never paid")
        void sanctionedPayeeBlocked() {
            BatchPlan plan = planner.plan(
                    List.of(with(worker("1000"), true, false, false, true)));

            assertThat(plan.blocked().getFirst().eligibility().summary()).contains("sanctions");
        }

        @Test
        @DisplayName("a disputed transaction holds its payout")
        void disputeHolds() {
            BatchPlan plan = planner.plan(
                    List.of(with(worker("2000"), true, false, true, false)));

            assertThat(plan.blocked().getFirst().eligibility().summary()).contains("dispute");
        }

        @Test
        @DisplayName("a large payout without KYC is held, but a small one is not")
        void kycThresholdApplies() {
            BatchPlan large = planner.plan(
                    List.of(with(worker("90000"), false, false, false, false)));
            BatchPlan small = planner.plan(
                    List.of(with(worker("5000"), false, false, false, false)));

            assertThat(large.blocked().getFirst().eligibility().summary()).contains("KYC");
            assertThat(small.payableCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("an amount above the single-payout ceiling is held for manual release")
        void ceilingApplies() {
            BatchPlan plan = planner.plan(List.of(worker("600000")));

            assertThat(plan.blocked().getFirst().eligibility().summary()).contains("ceiling");
        }

        @Test
        @DisplayName("a payee with no destination on file cannot be paid")
        void missingDestinationBlocks() {
            PayeeSnapshot noPhone = new PayeeSnapshot(UUID.randomUUID(), "No Phone", null,
                    new BigDecimal("100"), true, false, false, false);

            assertThat(planner.plan(List.of(noPhone)).blocked().getFirst()
                    .eligibility().summary()).contains("no payout destination");
        }

        @Test
        @DisplayName("a zero or negative amount is refused")
        void nonPositiveAmountBlocked() {
            PayeeSnapshot zero = new PayeeSnapshot(UUID.randomUUID(), "Zero", "254712345678",
                    BigDecimal.ZERO, true, false, false, false);

            assertThat(planner.plan(List.of(zero)).blockedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("every blocking reason is reported, so an investigator sees the whole picture")
        void allReasonsReported() {
            PayeeSnapshot bad = new PayeeSnapshot(UUID.randomUUID(), "Bad", null,
                    new BigDecimal("90000"), false, true, true, true);

            assertThat(planner.plan(List.of(bad)).blocked().getFirst()
                    .eligibility().blockingReasons()).hasSize(5);
        }
    }

    @Test
    @DisplayName("a mixed batch pays the clean payees and holds only the rest")
    void mixedBatchPartiallyPays() {
        BatchPlan plan = planner.plan(List.of(
                worker("4500"),
                worker("3000"),
                with(worker("7000"), true, true, false, false),
                with(worker("2500"), true, false, true, false)));

        assertThat(plan.payableCount())
                .as("one worker's fraud case must not stop everyone else being paid")
                .isEqualTo(2);
        assertThat(plan.blockedCount()).isEqualTo(2);
        assertThat(plan.payableTotal()).isEqualByComparingTo("7500");
        assertThat(plan.blockedTotal()).isEqualByComparingTo("9500");
    }

    @Test
    @DisplayName("an empty batch plans cleanly rather than failing")
    void emptyBatch() {
        BatchPlan plan = planner.plan(List.of());

        assertThat(plan.payableCount()).isZero();
        assertThat(plan.payableTotal()).isEqualByComparingTo("0");
    }
}
