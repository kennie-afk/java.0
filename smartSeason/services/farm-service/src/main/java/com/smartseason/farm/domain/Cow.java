package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cows", indexes = {
        @Index(name = "ix_cows_farm_id", columnList = "farm_id"),
        @Index(name = "ix_cows_tag_no", columnList = "tag_no"),
        @Index(name = "ix_cows_dam_id", columnList = "dam_id")
})
public class Cow extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "tag_no", nullable = false)
    private String tagNo;

    @Column(name = "name")
    private String name;

    @Column(name = "breed")
    private String breed;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false)
    private Sex sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "dam_id")
    private UUID damId;

    @Column(name = "sire_ref")
    private String sireRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Column(name = "exited_on")
    private LocalDate exitedOn;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getTagNo() { return tagNo; }
    public void setTagNo(String tagNo) { this.tagNo = tagNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public Sex getSex() { return sex; }
    public void setSex(Sex sex) { this.sex = sex; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public UUID getDamId() { return damId; }
    public void setDamId(UUID damId) { this.damId = damId; }

    public String getSireRef() { return sireRef; }
    public void setSireRef(String sireRef) { this.sireRef = sireRef; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDate getAcquiredOn() { return acquiredOn; }
    public void setAcquiredOn(LocalDate acquiredOn) { this.acquiredOn = acquiredOn; }

    public LocalDate getExitedOn() { return exitedOn; }
    public void setExitedOn(LocalDate exitedOn) { this.exitedOn = exitedOn; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Sex { FEMALE, MALE }

    public enum Status { MILKING, DRY, HEIFER, CALF, BULL, SOLD, DEAD }

}
