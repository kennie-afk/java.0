package com.kenyarealestate.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "failed_events")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FailedEvent {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;

    @Column(name = "event_type")
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Builder.Default
    @Column(nullable = false)
    private boolean resolved = false;

    @CreationTimestamp
    @Column(name = "received_at")
    private LocalDateTime receivedAt;
}
