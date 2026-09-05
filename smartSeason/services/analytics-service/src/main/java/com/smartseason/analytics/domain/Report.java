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
import java.util.UUID;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "ix_reports_code", columnList = "code"),
        @Index(name = "ix_reports_category", columnList = "category")
})
public class Report extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category")
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_spec", nullable = false, columnDefinition = "jsonb")
    private String querySpec;

    @Column(name = "schedule")
    private String schedule;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private Format format;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getQuerySpec() { return querySpec; }
    public void setQuerySpec(String querySpec) { this.querySpec = querySpec; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public Format getFormat() { return format; }
    public void setFormat(Format format) { this.format = format; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public enum Format { JSON, CSV, PDF, XLSX }

}
