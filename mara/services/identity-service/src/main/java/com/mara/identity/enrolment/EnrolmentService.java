package com.mara.identity.enrolment;

import com.mara.platform.identity.EnrolmentCode;
import com.mara.platform.identity.EnrolmentOutcome;
import com.mara.platform.identity.EnrolmentPolicy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admits a terminal to a tenant, or refuses it.
 *
 * <p>The decision itself lives in the pure {@link EnrolmentPolicy}; this class supplies
 * the state the policy needs and makes the outcome durable. Keeping those apart is what
 * lets the rules be tested exhaustively without a database, and what keeps this class
 * small enough to read in one sitting.
 */
@Service
public class EnrolmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrolmentService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnrolmentRepository repository;
    private final EnrolmentPolicy policy;
    private final Clock clock;

    public EnrolmentService(EnrolmentRepository repository, EnrolmentPolicy policy, Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Enrols a device presenting a code and a freshly generated public key.
     *
     * <p>Transactional because the redemption and the terminal insert must both happen
     * or neither: a redeemed code with no terminal locks the owner out of a licence
     * slot, and a terminal with an unredeemed code lets the same code enrol a second
     * device.
     */
    @Transactional
    public EnrolmentOutcome enrol(String submittedCode, String publicKeyBase64, String label) {
        Instant now = clock.instant();

        // Hashed before anything is looked up: the plaintext must never reach the
        // database — not in a query, not in a log, not in a statement trace.
        byte[] lookupHash = EnrolmentCode.hash(submittedCode);
        Optional<EnrolmentRepository.Resolved> found = repository.resolve(lookupHash);

        if (found.isEmpty()) {
            // Deliberately identical to every other rejection at the API boundary.
            log.info("enrolment rejected: no matching code");
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.UNKNOWN_CODE);
        }

        EnrolmentRepository.Resolved resolved = found.get();

        // The device now has a tenant — one the server derived from the code, not one
        // the caller claimed. Adopt it for the rest of this transaction so the terminal
        // insert below is checked by the same row-level policy as every other write.
        repository.bindTenant(resolved.tenantId());

        String terminalId = newTerminalId();

        EnrolmentOutcome outcome = policy.evaluate(
                resolved.toPending(lookupHash),
                submittedCode,
                publicKeyBase64,
                repository.isKeyRegistered(publicKeyBase64),
                now,
                terminalId);

        if (!outcome.accepted()) {
            // The reason is logged for the owner and the auditor, both of whom are behind
            // authentication. It is never returned to the device.
            log.info("enrolment rejected for tenant {}: {}", resolved.tenantId(), outcome.reason());
            return outcome;
        }

        // Redemption is the serialisation point. Two devices that both passed policy
        // arrive here; the conditional UPDATE inside redeem_enrolment lets exactly one
        // through, and the loser is refused even though its own checks passed.
        if (!repository.redeem(resolved.enrolmentId(), terminalId, now)) {
            log.warn("enrolment lost the redemption race for tenant {}", resolved.tenantId());
            return EnrolmentOutcome.rejected(EnrolmentOutcome.Reason.ALREADY_REDEEMED);
        }

        repository.insertPendingTerminal(
                terminalId, resolved.tenantId(), resolved.branchId(), label, publicKeyBase64, now);

        log.info("terminal {} enrolled to tenant {}", terminalId, resolved.tenantId());
        return outcome;
    }

    private static String newTerminalId() {
        byte[] entropy = new byte[10];
        RANDOM.nextBytes(entropy);
        return "TERM-" + HexFormat.of().formatHex(entropy).toUpperCase();
    }
}
