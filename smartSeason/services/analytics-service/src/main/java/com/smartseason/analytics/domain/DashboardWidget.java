package com.smartseason.analytics.domain;

import com.smartseason.analytics.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboard_widgets", indexes = {
        @Index(name = "ix_dashboard_widgets_dashboard_code", columnList = "dashboard_code")
})
public class DashboardWidget extends BaseEntity {

    @Column(name = "dashboard_code", nullable = false)
    private String dashboardCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_type", nullable = false)
    private WidgetType widgetType;

    @Column(name = "metric_key")
    private String metricKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_spec", columnDefinition = "jsonb")
    private String querySpec;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "width")
    private Integer width;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    public String getDashboardCode() { return dashboardCode; }
    public void setDashboardCode(String dashboardCode) { this.dashboardCode = dashboardCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public WidgetType getWidgetType() { return widgetType; }
    public void setWidgetType(WidgetType widgetType) { this.widgetType = widgetType; }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getQuerySpec() { return querySpec; }
    public void setQuerySpec(String querySpec) { this.querySpec = querySpec; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public enum WidgetType { KPI, LINE, BAR, PIE, TABLE, MAP }

}
