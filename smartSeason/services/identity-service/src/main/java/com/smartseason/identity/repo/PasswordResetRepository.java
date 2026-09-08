package com.smartseason.identity.repo;

import com.smartseason.identity.domain.OtpChallenge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Reset-code lookups. Separate from the generated repository so a regeneration
 * cannot drop them.
 */
@Repository
public interface PasswordResetRepository extends JpaRepository<OtpChallenge, UUID> {

    /** The newest unconsumed challenge for a destination and purpose. */
    Optional<OtpChallenge> findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String destination, String purpose);
}
