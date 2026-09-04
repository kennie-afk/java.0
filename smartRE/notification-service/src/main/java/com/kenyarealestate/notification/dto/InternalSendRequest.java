package com.kenyarealestate.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InternalSendRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    private String templateCode;

    private String recipientEmail;

    private String dedupKey;

    private String entityType;
    private UUID entityId;
    private String actionUrl;

    @Builder.Default
    private Map<String, Object> model = Map.of();
}
