package com.kenyarealestate.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "property_outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    /** The Kafka partition key the original send used; a retry must reuse it exactly. */
    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}
