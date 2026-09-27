package com.mara.platform.staff;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PinPolicyTest {

    private final PinPolicy policy = new PinPolicy();

    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");
    private static final String BRANCH_A = "BRANCH-A";
    private static final String BRANCH_B = "BRANCH-B";

    private static PinPolicy.StaffState staff(
            StaffRole role, String branchId, boolean active, int failedAttempts, Instant lockedUntil) {
        return new PinPolicy.StaffState("STAFF-1", role, branchId, active, failedAttempts, lockedUntil);
    }

    @Nested
    @DisplayName("signing in")
    class SigningIn {

        @Test
        void unknownStaffNumberIsRejected() {
            SignInOutcome outcome = policy.evaluate(null, false, BRANCH_A, NOW);

            assertEquals(SignInOutcome.Result.REJECTED, outcome.outcome());
        }

        @Test
        void unknownStaffAndWrongPinLookIdentical() {
            // Deliberately the same case as above: telling the two apart at the wire
            // halves an attacker's work before they start.
            SignInOutcome unknown = policy.evaluate(null, false, BRANCH_A, NOW);
            SignInOutcome wrongPin = policy.evaluate(
                    staff(StaffRole.CASHIER, BRANCH_A, true, 0, null), false, BRANCH_A, NOW);

            assertEquals(unknown.outcome(), wrongPin.outcome());
            assertNull(unknown.staffId());
            assertNull(wrongPin.staffId());
        }

        @Test
        void aLockedAccountIsRefusedBeforeThePinIsChecked() {
            PinPolicy.StaffState locked =
                    staff(StaffRole.CASHIER, BRANCH_A, true, 5, NOW.plus(Duration.ofMinutes(1)));

            // pinMatches is true here on purpose: even a correct PIN must not get past
            // the lockout, or the lockout leaks whether the PIN was right.
            SignInOutcome outcome = policy.evaluate(locked, true, BRANCH_A, NOW);

            assertEquals(SignInOutcome.Result.LOCKED, outcome.outcome());
            assertEquals(locked.lockedUntil(), outcome.lockedUntil());
        }

        @Test
        void aLockoutThatHasExpiredNoLongerBlocksSignIn() {
            PinPolicy.StaffState expiredLock =
                    staff(StaffRole.CASHIER, BRANCH_A, true, 5, NOW.minus(Duration.ofSeconds(1)));

            SignInOutcome outcome = policy.evaluate(expiredLock, true, BRANCH_A, NOW);

            assertTrue(outcome.succeeded());
        }

        @Test
        void aSuspendedStaffMemberIsRefusedEvenWithTheCorrectPin() {
            PinPolicy.StaffState suspended = staff(StaffRole.CASHIER, BRANCH_A, false, 0, null);

            SignInOutcome outcome = policy.evaluate(suspended, true, BRANCH_A, NOW);

            assertEquals(SignInOutcome.Result.NOT_ACTIVE, outcome.outcome());
        }

        @Test
        void aWrongPinIsRefused() {
            PinPolicy.StaffState active = staff(StaffRole.CASHIER, BRANCH_A, true, 0, null);

            SignInOutcome outcome = policy.evaluate(active, false, BRANCH_A, NOW);

            assertEquals(SignInOutcome.Result.REJECTED, outcome.outcome());
        }

        @Test
        void correctPinAtAnotherBranchIsRefused() {
            PinPolicy.StaffState cashier = staff(StaffRole.CASHIER, BRANCH_A, true, 0, null);

            SignInOutcome outcome = policy.evaluate(cashier, true, BRANCH_B, NOW);

            assertEquals(SignInOutcome.Result.WRONG_BRANCH, outcome.outcome());
        }

        @Test
        void correctPinAtTheOwnBranchSucceeds() {
            PinPolicy.StaffState cashier = staff(StaffRole.CASHIER, BRANCH_A, true, 0, null);

            SignInOutcome outcome = policy.evaluate(cashier, true, BRANCH_A, NOW);

            assertTrue(outcome.succeeded());
            assertEquals("STAFF-1", outcome.staffId());
        }

        @Test
        void anOwnerSignsInAtAnyBranch() {
            PinPolicy.StaffState owner = staff(StaffRole.OWNER, null, true, 0, null);

            SignInOutcome outcome = policy.evaluate(owner, true, BRANCH_B, NOW);

            assertTrue(outcome.succeeded());
        }
    }

    @Nested
    @DisplayName("the lockout grows with repeated failure")
    class Lockout {

        @Test
        void earlyMistypesCostNothing() {
            for (int failures = 0; failures < PinPolicy.ATTEMPTS_BEFORE_LOCKOUT; failures++) {
                assertEquals(Duration.ZERO, policy.lockoutAfterFailure(failures));
            }
        }

        @Test
        void theLockoutDoublesEachFurtherFailure() {
            int base = PinPolicy.ATTEMPTS_BEFORE_LOCKOUT;

            assertEquals(Duration.ofMinutes(1), policy.lockoutAfterFailure(base));
            assertEquals(Duration.ofMinutes(2), policy.lockoutAfterFailure(base + 1));
            assertEquals(Duration.ofMinutes(4), policy.lockoutAfterFailure(base + 2));
            assertEquals(Duration.ofMinutes(8), policy.lockoutAfterFailure(base + 3));
        }

        @Test
        void theLockoutNeverExceedsTheCap() {
            int base = PinPolicy.ATTEMPTS_BEFORE_LOCKOUT;

            assertEquals(Duration.ofMinutes(30), policy.lockoutAfterFailure(base + 10));
        }

        @Test
        void anExtremeFailureCountStillReturnsTheCapRatherThanOverflowing() {
            // 1L << doublings would already have overflowed a long well before this;
            // the guard must return the cap directly instead of shifting first.
            assertEquals(Duration.ofMinutes(30), policy.lockoutAfterFailure(Integer.MAX_VALUE));
        }
    }

    @Nested
    @DisplayName("authorising an elevated action")
    class Authorising {

        @Test
        void aCashierMayAuthoriseNothing() {
            PinPolicy.StaffState cashier = staff(StaffRole.CASHIER, BRANCH_A, true, 0, null);

            assertFalse(policy.mayAuthorise(cashier, ElevatedAction.VOID_LINE, BRANCH_A, NOW));
        }

        @Test
        void aSupervisorMayAuthoriseASupervisorLevelAction() {
            PinPolicy.StaffState supervisor = staff(StaffRole.SUPERVISOR, BRANCH_A, true, 0, null);

            assertTrue(policy.mayAuthorise(supervisor, ElevatedAction.VOID_LINE, BRANCH_A, NOW));
        }

        @Test
        void aSupervisorMayNotAuthoriseAManagerLevelAction() {
            PinPolicy.StaffState supervisor = staff(StaffRole.SUPERVISOR, BRANCH_A, true, 0, null);

            assertFalse(policy.mayAuthorise(supervisor, ElevatedAction.REFUND, BRANCH_A, NOW));
        }

        @Test
        void onlyAnOwnerMayAuthoriseAnOwnerLevelAction() {
            PinPolicy.StaffState manager = staff(StaffRole.MANAGER, BRANCH_A, true, 0, null);
            PinPolicy.StaffState owner = staff(StaffRole.OWNER, null, true, 0, null);

            assertFalse(policy.mayAuthorise(manager, ElevatedAction.MANAGE_STAFF, BRANCH_A, NOW));
            assertTrue(policy.mayAuthorise(owner, ElevatedAction.MANAGE_STAFF, BRANCH_A, NOW));
        }

        @Test
        void aSupervisorCannotAuthoriseAtAnotherBranch() {
            PinPolicy.StaffState supervisor = staff(StaffRole.SUPERVISOR, BRANCH_A, true, 0, null);

            assertFalse(policy.mayAuthorise(supervisor, ElevatedAction.VOID_LINE, BRANCH_B, NOW));
        }

        @Test
        void anOwnerCanAuthoriseAtAnyBranch() {
            PinPolicy.StaffState owner = staff(StaffRole.OWNER, null, true, 0, null);

            assertTrue(policy.mayAuthorise(owner, ElevatedAction.MANAGE_STAFF, BRANCH_B, NOW));
        }

        @Test
        void aLockedSupervisorMayNotAuthorise() {
            PinPolicy.StaffState locked =
                    staff(StaffRole.SUPERVISOR, BRANCH_A, true, 5, NOW.plus(Duration.ofMinutes(1)));

            assertFalse(policy.mayAuthorise(locked, ElevatedAction.VOID_LINE, BRANCH_A, NOW));
        }

        @Test
        void aSuspendedSupervisorMayNotAuthorise() {
            PinPolicy.StaffState suspended = staff(StaffRole.SUPERVISOR, BRANCH_A, false, 0, null);

            assertFalse(policy.mayAuthorise(suspended, ElevatedAction.VOID_LINE, BRANCH_A, NOW));
        }

        @Test
        void aNullAuthoriserMayNotAuthorise() {
            assertFalse(policy.mayAuthorise(null, ElevatedAction.VOID_LINE, BRANCH_A, NOW));
        }
    }
}
