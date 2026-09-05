package com.smartseason.identity.repo;

import com.smartseason.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    Page<User> findAllByPhoneAndTenantId(String phone, UUID tenantId, Pageable pageable);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
