package com.smartseason.catalog.domain;

import com.smartseason.catalog.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "grade_standards", indexes = {
        @Index(name = "ix_grade_standards_commodity_code", columnList = "commodity_code")
})
public class GradeStandard extends BaseEntity {

    @Column(name = "commodity_code", nullable = false)
    private String commodityCode;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name = "criteria", nullable = false, columnDefinition = "TEXT")
    private String criteria;

    @Column(name = "min_size_mm")
    private BigDecimal minSizeMm;

    @Column(name = "max_defect_pct")
    private BigDecimal maxDefectPct;

    @Column(name = "moisture_pct_max")
    private BigDecimal moisturePctMax;

    @Column(name = "revision", nullable = false)
    private Integer revision;

    public String getCommodityCode() { return commodityCode; }
    public void setCommodityCode(String commodityCode) { this.commodityCode = commodityCode; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getCriteria() { return criteria; }
    public void setCriteria(String criteria) { this.criteria = criteria; }

    public BigDecimal getMinSizeMm() { return minSizeMm; }
    public void setMinSizeMm(BigDecimal minSizeMm) { this.minSizeMm = minSizeMm; }

    public BigDecimal getMaxDefectPct() { return maxDefectPct; }
    public void setMaxDefectPct(BigDecimal maxDefectPct) { this.maxDefectPct = maxDefectPct; }

    public BigDecimal getMoisturePctMax() { return moisturePctMax; }
    public void setMoisturePctMax(BigDecimal moisturePctMax) { this.moisturePctMax = moisturePctMax; }

    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }

}
