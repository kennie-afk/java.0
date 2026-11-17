package com.smartseason.agronomy.casework;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMessageRepository extends JpaRepository<CaseMessage, UUID> {

    List<CaseMessage> findByTenantIdAndCaseIdOrderByCreatedAtAscIdAsc(UUID tenantId, UUID caseId, Pageable pageable);
}
