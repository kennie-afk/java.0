package com.smartseason.agronomy.domain;

import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "pest_diseases", indexes = {
        @Index(name = "ix_pest_diseases_code", columnList = "code")
})
public class PestDisease extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "common_name", nullable = false)
    private String commonName;

    @Column(name = "scientific_name")
    private String scientificName;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "affected_crops")
    private String affectedCrops;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "management", columnDefinition = "TEXT")
    private String management;

    @Column(name = "image_url")
    private String imageUrl;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCommonName() { return commonName; }
    public void setCommonName(String commonName) { this.commonName = commonName; }

    public String getScientificName() { return scientificName; }
    public void setScientificName(String scientificName) { this.scientificName = scientificName; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getAffectedCrops() { return affectedCrops; }
    public void setAffectedCrops(String affectedCrops) { this.affectedCrops = affectedCrops; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getManagement() { return management; }
    public void setManagement(String management) { this.management = management; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public enum Type { PEST, DISEASE, WEED, DEFICIENCY }

}
