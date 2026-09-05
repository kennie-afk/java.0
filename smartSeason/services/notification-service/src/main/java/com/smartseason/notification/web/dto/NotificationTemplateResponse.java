package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.NotificationTemplate;
import java.time.Instant;
import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        String code,
        NotificationTemplate.Channel channel,
        String locale,
        String subject,
        String body,
        String variables,
        Boolean active,
        Integer revision,
        Instant createdAt,
        Instant updatedAt) {

    public static NotificationTemplateResponse from(NotificationTemplate entity) {
        return new NotificationTemplateResponse(
                entity.getId(),
                entity.getCode(),
                entity.getChannel(),
                entity.getLocale(),
                entity.getSubject(),
                entity.getBody(),
                entity.getVariables(),
                entity.getActive(),
                entity.getRevision(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
