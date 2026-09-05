package com.smartseason.workforce.domain;

import com.smartseason.workforce.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gang_memberships", indexes = {
        @Index(name = "ix_gang_memberships_gang_id", columnList = "gang_id"),
        @Index(name = "ix_gang_memberships_worker_id", columnList = "worker_id")
})
public class GangMembership extends BaseEntity {

    @Column(name = "gang_id", nullable = false)
    private UUID gangId;

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    public UUID getGangId() { return gangId; }
    public void setGangId(UUID gangId) { this.gangId = gangId; }

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public Instant getLeftAt() { return leftAt; }
    public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public enum Role { MEMBER, LEAD }

}
