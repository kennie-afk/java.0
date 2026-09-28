package com.kenyarealestate.pms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "landlord_id", nullable = false)
    private UUID landlordId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "national_id", length = 40)
    private String nationalId;

    @Column(name = "emergency_name", length = 160)
    private String emergencyName;

    @Column(name = "emergency_phone", length = 32)
    private String emergencyPhone;

    /**
     * SHA-256 of the outstanding invitation token, or null when there is none.
     *
     * <p>The hash, never the token. Whoever holds the plaintext can claim this tenancy, so
     * storing it would put a bearer credential in the table and in every backup of it.
     */
    @Column(name = "invite_token_hash")
    private byte[] inviteTokenHash;

    @Column(name = "invite_expires_at")
    private java.time.Instant inviteExpiresAt;

    @Column(name = "invite_sent_at")
    private java.time.Instant inviteSentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
