package com.smartseason.catalog.domain;

import com.smartseason.catalog.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "certifications", indexes = {
        @Index(name = "ix_certifications_code", columnList = "code")
})
public class Certification extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "issuing_body")
    private String issuingBody;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "validity_months")
    private Integer validityMonths;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIssuingBody() { return issuingBody; }
    public void setIssuingBody(String issuingBody) { this.issuingBody = issuingBody; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getValidityMonths() { return validityMonths; }
    public void setValidityMonths(Integer validityMonths) { this.validityMonths = validityMonths; }

}
