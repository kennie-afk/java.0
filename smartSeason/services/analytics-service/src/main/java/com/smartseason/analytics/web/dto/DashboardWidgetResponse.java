package com.smartseason.analytics.web.dto;

import com.smartseason.analytics.domain.DashboardWidget;
import java.time.Instant;
import java.util.UUID;

public record DashboardWidgetResponse(
        UUID id,
        String dashboardCode,
        String title,
        DashboardWidget.WidgetType widgetType,
        String metricKey,
        String querySpec,
        Integer position,
        Integer width,
        String config,
        Instant createdAt,
        Instant updatedAt) {

    public static DashboardWidgetResponse from(DashboardWidget entity) {
        return new DashboardWidgetResponse(
                entity.getId(),
                entity.getDashboardCode(),
                entity.getTitle(),
                entity.getWidgetType(),
                entity.getMetricKey(),
                entity.getQuerySpec(),
                entity.getPosition(),
                entity.getWidth(),
                entity.getConfig(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
