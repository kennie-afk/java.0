package com.kenyarealestate.notification.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String category;
    private String templateCode;
    private String subject;
    private String body;
    private String entityType;
    private UUID entityId;
    private String actionUrl;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
