package com.smartseason.identity.repo;

import com.smartseason.identity.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findAllByTenantId(UUID tenantId, Pageable pageable);

    Slice<User> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM User e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<User> findAfterCursor(@Param("tenantId") UUID tenantId,
                              @Param("cursorAt") Instant cursorAt,
                              @Param("cursorId") UUID cursorId,
                              Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

    Page<User> findAllByPhoneAndTenantId(String phone, UUID tenantId, Pageable pageable);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
