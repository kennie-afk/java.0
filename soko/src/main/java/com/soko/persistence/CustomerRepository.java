package com.soko.persistence;

import com.soko.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantId(UUID tenantId);
}
