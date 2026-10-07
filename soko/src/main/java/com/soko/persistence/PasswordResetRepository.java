package com.soko.persistence;

import com.soko.domain.PasswordReset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    /** Single use: only one caller can flip a token from unused to used. */
    @Transactional
    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.id = :id and r.usedAt is null and r.expiresAt > :now")
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    /** A new request voids earlier unused tokens for the same account. */
    @Transactional
    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.userId = :userId and r.usedAt is null")
    int voidOutstanding(@Param("userId") UUID userId, @Param("now") Instant now);
}
