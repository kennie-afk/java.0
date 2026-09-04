package com.kenyarealestate.notification.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminNotificationResponse {
    private UUID id;
    private UUID userId;
    private String recipientEmail;
    private String channel;
    private String category;
    private String templateCode;
    private String subject;
    private String status;
    private int attempts;
    private String lastError;
    private String sourceEventType;
    private String sourceEventId;
    private LocalDateTime createdAt;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime sentAt;
}
