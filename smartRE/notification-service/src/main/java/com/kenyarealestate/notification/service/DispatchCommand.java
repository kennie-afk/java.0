package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Category;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DispatchCommand {

    private UUID userId;
    private String templateCode;
    private Category category;

    private String recipientEmail;

    private String sourceEventType;
    private String sourceEventId;

    private String entityType;
    private UUID entityId;
    private String actionUrl;

    private String dedupKeyOverride;

    @Builder.Default
    private Map<String, Object> model = Map.of();
}
