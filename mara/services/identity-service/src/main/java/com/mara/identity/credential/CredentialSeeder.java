package com.mara.identity.credential;

import com.mara.platform.credential.OperatorToken;
import com.mara.platform.credential.Scopes;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registers, at start-up, the credentials a deployer minted for the two other services (and,
 * optionally, a short-lived bootstrap credential for creating the first platform operator).
 * The secrets live only in the deployer's environment and in each service's own; the database
 * holds their hashes.
 *
 * <p>Rotation: put the new credential in {@code MARA_SVC_<SERVICE>_CREDENTIAL} and the old one in
 * {@code ..._PREVIOUS}, roll everything, then remove {@code _PREVIOUS} and restart identity: any
 * seeded credential for that service that is in neither is revoked.
 *
 * <p>The bootstrap credential can do exactly one thing, manage credentials, and expires 24 hours
 * after each start. Remove {@code MARA_BOOTSTRAP_CREDENTIAL} and restart and it is revoked.
 */
@Component
public class CredentialSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CredentialSeeder.class);

    private final OperatorCredentialService credentials;
    private final Clock clock;
    private final boolean requireServices;
    private final String bootstrap;
    private final String syncCredential;
    private final String syncPrevious;
    private final String coreCredential;
    private final String corePrevious;

    public CredentialSeeder(
            OperatorCredentialService credentials, Clock clock,
            @Value("${mara.credential.require-services:true}") boolean requireServices,
            @Value("${mara.credential.seed.bootstrap:}") String bootstrap,
            @Value("${mara.credential.seed.sync:}") String syncCredential,
            @Value("${mara.credential.seed.sync-previous:}") String syncPrevious,
            @Value("${mara.credential.seed.core:}") String coreCredential,
            @Value("${mara.credential.seed.core-previous:}") String corePrevious) {
        this.credentials = credentials;
        this.clock = clock;
        this.requireServices = requireServices;
        this.bootstrap = bootstrap;
        this.syncCredential = syncCredential;
        this.syncPrevious = syncPrevious;
        this.coreCredential = coreCredential;
        this.corePrevious = corePrevious;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (requireServices && (blank(syncCredential) || blank(coreCredential))) {
            throw new IllegalStateException(
                    "MARA_SVC_SYNC_CREDENTIAL and MARA_SVC_CORE_CREDENTIAL must be set (mint with scripts/new-credential.py)");
        }
        seedService("svc-sync", List.of(Scopes.TERMINALS_LOOKUP, Scopes.CREDENTIALS_VERIFY), syncCredential, syncPrevious);
        seedService("svc-core", List.of(Scopes.TERMINALS_LOOKUP, Scopes.CREDENTIALS_VERIFY, Scopes.SYNC_FEED),
                coreCredential, corePrevious);
        var token = parse("MARA_BOOTSTRAP_CREDENTIAL", bootstrap);
        int revoked = credentials.seed("bootstrap", "OPERATOR", List.of(Scopes.CREDENTIALS_MANAGE), token,
                token == null ? List.of() : List.of(token.keyId()), clock.instant().plus(Duration.ofHours(24)));
        if (token != null) {
            log.warn("A bootstrap credential is registered (expires in 24 h). Mint a platform operator credential with it, then remove MARA_BOOTSTRAP_CREDENTIAL and restart.");
        } else if (revoked > 0) {
            log.info("Bootstrap credential revoked: MARA_BOOTSTRAP_CREDENTIAL is no longer set");
        }
    }

    private void seedService(String label, List<String> scopes, String current, String previous) {
        var token = parse("credential for " + label, current);
        if (token == null) {
            return;
        }
        List<String> keep = new ArrayList<>(List.of(token.keyId()));
        var prior = parse("previous credential for " + label, previous);
        if (prior != null) {
            keep.add(prior.keyId());
            credentials.seed(label, "SERVICE", scopes, prior, keep, clock.instant().plus(Duration.ofDays(365)));
        }
        int revoked = credentials.seed(label, "SERVICE", scopes, token, keep, clock.instant().plus(Duration.ofDays(365)));
        if (revoked > 0) {
            log.info("{}: revoked {} rotated-out credential(s)", label, revoked);
        }
    }

    private static OperatorToken.Parsed parse(String what, String value) {
        if (blank(value)) {
            return null;
        }
        return OperatorToken.parse(value).orElseThrow(() ->
                new IllegalStateException(what + " is not a valid credential (expected mop_<16 hex>.<43 characters>)"));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
