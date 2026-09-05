package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.NotificationTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateUpdateRequest(
        @Size(max = 255) String code,
        NotificationTemplate.Channel channel,
        @Size(max = 255) String locale,
        @Size(max = 255) String subject,
        String body,
        @Size(max = 255) String variables,
        Boolean active,
        Integer revision) {
}
