package com.smartseason.identity.repo;

import com.smartseason.identity.domain.Organisation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    Optional<Organisation> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Organisation> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Organisation> findByRegistrationNoAndTenantId(String registrationNo, UUID tenantId);
}
