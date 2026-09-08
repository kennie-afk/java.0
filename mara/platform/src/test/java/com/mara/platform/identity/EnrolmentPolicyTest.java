package com.mara.platform.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.mara.platform.identity.EnrolmentOutcome.Reason;
import com.mara.platform.identity.EnrolmentPolicy.PendingEnrolment;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EnrolmentPolicyTest {

    private static final String TENANT = "TEN-01H8X";
    private static final String NEW_TERMINAL = "TERM-LANE-07";
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    private final EnrolmentPolicy policy = new EnrolmentPolicy();

    @Nested
    @DisplayName("a legitimate enrolment")
    class Accepted {

        @Test
        void enrolsWhenCodeKeyAndLicenceAllHold() {
            String code = EnrolmentCode.generate();
            EnrolmentOutcome outcome = policy.evaluate(
                    pending(code, false, 3, 8), code, freshKey(), false, NOW, NEW_TERMINAL);

            assertTrue(outcome.accepted());
            assertEquals(Reason.ENROLLED, outcome.reason());
            assertEquals(NEW_TERMINAL, outcome.terminalId());
        }

        @Test
        void toleratesHowAPersonActuallyTypesTheCode() {
            // Read aloud across a shop floor and typed back in. Lower case, no hyphen,
            // stray spaces — all must land on the same value, or valid codes fail for
            // cosmetic reasons and the owner reissues, which is worse.
            String code = EnrolmentCode.generate();
            String asTyped = " " + code.toLowerCase().replace("-", "") + " ";

            assertTrue(policy.evaluate(
                    pending(code, false, 0, 8), asTyped, freshKey(), false, NOW, NEW_TERMINAL)
                    .accepted());
        }
    }

    @Nested
    @DisplayName("a hostile or stale enrolment")
    class Rejected {

        @Test
        void unknownCodeIsRejectedWithoutRevealingWhy() {
            assertEquals(Reason.UNKNOWN_CODE,
                    policy.evaluate(null, "ANY-CODE", freshKey(), false, NOW, NEW_TERMINAL).reason());
        }

        @Test
        void wrongCodeLooksIdenticalToNoCode() {
            // The two must be indistinguishable, or the pair is a search procedure.
            EnrolmentOutcome wrong = policy.evaluate(
                    pending(EnrolmentCode.generate(), false, 0, 8),
                    EnrolmentCode.generate(), freshKey(), false, NOW, NEW_TERMINAL);

            assertEquals(Reason.UNKNOWN_CODE, wrong.reason());
            assertNull(wrong.terminalId());
        }

        @Test
        void aCodeCannotEnrolASecondTerminal() {
            String code = EnrolmentCode.generate();
            assertEquals(Reason.ALREADY_REDEEMED,
                    policy.evaluate(pending(code, true, 1, 8), code, freshKey(), false, NOW, NEW_TERMINAL)
                            .reason());
        }

        @Test
        void aPhotographedCodeIsUselessAfterFifteenMinutes() {
            String code = EnrolmentCode.generate();
            Instant later = NOW.plus(EnrolmentCode.DEFAULT_VALIDITY);

            assertEquals(Reason.EXPIRED,
                    policy.evaluate(pending(code, false, 0, 8), code, freshKey(), false, later, NEW_TERMINAL)
                            .reason());
        }

        @Test
        void expiryIsInclusiveAtTheBoundary() {
            String code = EnrolmentCode.generate();
            PendingEnrolment stored = pending(code, false, 0, 8);

            Instant justInside = NOW.plus(EnrolmentCode.DEFAULT_VALIDITY).minusMillis(1);
            assertTrue(policy.evaluate(stored, code, freshKey(), false, justInside, NEW_TERMINAL).accepted());

            Instant exactly = NOW.plus(EnrolmentCode.DEFAULT_VALIDITY);
            assertEquals(Reason.EXPIRED,
                    policy.evaluate(stored, code, freshKey(), false, exactly, NEW_TERMINAL).reason());
        }

        @Test
        void rubbishInsteadOfAKeyIsRejected() {
            String code = EnrolmentCode.generate();
            assertEquals(Reason.MALFORMED_KEY,
                    policy.evaluate(pending(code, false, 0, 8), code, "not-a-key", false, NOW, NEW_TERMINAL)
                            .reason());
        }

        @Test
        void aClonedDeviceCannotReuseAnotherTerminalsKey() {
            String code = EnrolmentCode.generate();
            assertEquals(Reason.KEY_ALREADY_REGISTERED,
                    policy.evaluate(pending(code, false, 0, 8), code, freshKey(), true, NOW, NEW_TERMINAL)
                            .reason());
        }

        @Test
        void enrolmentStopsAtTheLicensedTerminalCount() {
            String code = EnrolmentCode.generate();
            assertEquals(Reason.TERMINAL_LIMIT_REACHED,
                    policy.evaluate(pending(code, false, 8, 8), code, freshKey(), false, NOW, NEW_TERMINAL)
                            .reason());
        }
    }

    @Nested
    @DisplayName("code generation")
    class Codes {

        @Test
        void omitsCharactersPeopleConfuse() {
            // No 0/O, no 1/I/L — a code that has to be re-read gets written down.
            for (int i = 0; i < 500; i++) {
                String code = EnrolmentCode.normalise(EnrolmentCode.generate());
                assertFalse(code.matches(".*[01OIL].*"), "ambiguous character in " + code);
            }
        }

        @Test
        void doesNotRepeatItselfInPractice() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 5_000; i++) {
                assertTrue(seen.add(EnrolmentCode.generate()), "generated a duplicate code");
            }
        }

        @Test
        void hashingIsStableAcrossHowTheCodeIsWritten() {
            // Lookup depends on this: the device types the code however it likes, and
            // the resulting hash must still find the row the owner's code created.
            String code = EnrolmentCode.generate();
            byte[] canonical = EnrolmentCode.hash(code);

            assertArrayEquals(canonical, EnrolmentCode.hash(code.toLowerCase()));
            assertArrayEquals(canonical, EnrolmentCode.hash(code.replace("-", "")));
            assertArrayEquals(canonical, EnrolmentCode.hash("  " + code + "  "));
        }

        @Test
        void differentCodesDoNotCollide() {
            assertFalse(EnrolmentCode.matches(
                    EnrolmentCode.hash(EnrolmentCode.generate()), EnrolmentCode.generate()));
        }
    }

    @Test
    void rejectsNonPositiveValidityWindows() {
        assertThrows(IllegalArgumentException.class, () -> new EnrolmentPolicy(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new EnrolmentPolicy(Duration.ofMinutes(-1)));
    }

    private static PendingEnrolment pending(String code, boolean redeemed, int active, int licensed) {
        return new PendingEnrolment(
                TENANT, EnrolmentCode.hash(code), NOW, redeemed, active, licensed);
    }

    /** A real Ed25519 public key, encoded the way a terminal submits it. */
    static String freshKey() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }
}
