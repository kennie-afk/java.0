package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.DashboardWidget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DashboardWidgetCreateRequest(
        @NotBlank @Size(max = 255) String dashboardCode,
        @NotBlank @Size(max = 255) String title,
        @NotNull DashboardWidget.WidgetType widgetType,
        @Size(max = 255) String metricKey,
        String querySpec,
        @NotNull Integer position,
        Integer width,
        String config) {
}
