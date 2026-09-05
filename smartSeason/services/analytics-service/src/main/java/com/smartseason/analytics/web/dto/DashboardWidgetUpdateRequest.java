package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.DashboardWidget;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DashboardWidgetUpdateRequest(
        @Size(max = 255) String dashboardCode,
        @Size(max = 255) String title,
        DashboardWidget.WidgetType widgetType,
        @Size(max = 255) String metricKey,
        String querySpec,
        Integer position,
        Integer width,
        String config) {
}
