package com.smartseason.notification.web.dto;

import com.smartseason.notification.domain.NotificationTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotNull NotificationTemplate.Channel channel,
        @NotBlank @Size(max = 255) String locale,
        @Size(max = 255) String subject,
        @NotBlank String body,
        @Size(max = 255) String variables,
        @NotNull Boolean active,
        @NotNull Integer revision) {
}
