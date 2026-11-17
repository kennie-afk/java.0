package com.smartseason.agronomy.casework;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CasePhotoRepository extends JpaRepository<CasePhoto, UUID> {

    Optional<CasePhoto> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);

    /** Ids and sizes only: the image bytes are never read for a case view. */
    @Query("SELECT p.id, p.contentType, p.sizeBytes FROM CasePhoto p WHERE p.tenantId = :t AND p.caseId = :c "
            + "ORDER BY p.createdAt ASC, p.id ASC")
    List<Object[]> describe(@Param("t") UUID tenantId, @Param("c") UUID caseId);
}
