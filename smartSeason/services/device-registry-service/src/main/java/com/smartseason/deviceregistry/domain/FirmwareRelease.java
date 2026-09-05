package com.smartseason.deviceregistry.domain;

import com.smartseason.deviceregistry.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "firmware_releases", indexes = {
        @Index(name = "ix_firmware_releases_device_type", columnList = "device_type")
})
public class FirmwareRelease extends BaseEntity {

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "release_version", nullable = false)
    private String releaseVersion;

    @Column(name = "artifact_url", nullable = false)
    private String artifactUrl;

    @Column(name = "checksum", nullable = false)
    private String checksum;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "mandatory", nullable = false)
    private Boolean mandatory;

    @Column(name = "published_at")
    private Instant publishedAt;

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getReleaseVersion() { return releaseVersion; }
    public void setReleaseVersion(String releaseVersion) { this.releaseVersion = releaseVersion; }

    public String getArtifactUrl() { return artifactUrl; }
    public void setArtifactUrl(String artifactUrl) { this.artifactUrl = artifactUrl; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }

    public Boolean getMandatory() { return mandatory; }
    public void setMandatory(Boolean mandatory) { this.mandatory = mandatory; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

}
