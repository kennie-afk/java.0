package com.smartseason.identity.repo;

import com.smartseason.identity.domain.RefreshToken;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Ends every session a user holds. Used after a password change or reset, where
 * leaving other sessions alive would defeat the point of changing it.
 */
@Repository
public interface SessionRevocationRepository extends JpaRepository<RefreshToken, UUID> {

    List<RefreshToken> findAllByUserId(UUID userId);
}
