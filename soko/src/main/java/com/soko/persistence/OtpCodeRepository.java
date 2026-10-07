package com.soko.persistence;

import com.soko.domain.OtpCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every query here runs in a transaction, read-only unless a method says otherwise. That is not
 * only about Spring's defaults: the tenant is handed to the database when a transaction begins
 * (see TenantAwareDataSource), and a declared query method outside any transaction would run with
 * no tenant at all and, under row-level security, silently return nothing.
 */
@Transactional(readOnly = true)
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    // Most-recent-first: OtpService only ever wants the latest code for a
    // phone, and needs to invalidate any earlier ones when issuing a new one.
    List<OtpCode> findByTenantIdAndPhoneOrderByCreatedAtDesc(UUID tenantId, String phone);
}
