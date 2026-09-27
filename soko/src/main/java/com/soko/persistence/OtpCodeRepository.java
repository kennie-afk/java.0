package com.soko.persistence;

import com.soko.domain.OtpCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    // Most-recent-first: OtpService only ever wants the latest code for a
    // phone, and needs to invalidate any earlier ones when issuing a new one.
    List<OtpCode> findByTenantIdAndPhoneOrderByCreatedAtDesc(UUID tenantId, String phone);
}
