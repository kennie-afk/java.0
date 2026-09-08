package com.kenyarealestate.verification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "verification_id", nullable = false)
    private UUID verificationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "topic", nullable = false)
    private String topic;

    /** The Kafka partition key the original send used; a retry must reuse it exactly. */
    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
