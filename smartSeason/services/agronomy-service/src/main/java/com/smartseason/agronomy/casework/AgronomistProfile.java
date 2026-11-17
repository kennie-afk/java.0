package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Which counties an agronomist covers and how many cases they take at once. */
@Entity
@Table(name = "agronomist_profiles")
public class AgronomistProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "email")
    private String email;
    /** Comma-separated, lower-case. Empty means every county. */
    @Column(name = "counties", nullable = false)
    private String counties = "";
    @Column(name = "max_open", nullable = false)
    private int maxOpen = 25;
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public List<String> countyList() {
        return counties.isBlank() ? List.of()
                : Arrays.stream(counties.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean covers(String county) {
        List<String> list = countyList();
        return list.isEmpty() || (county != null && list.contains(county.trim().toLowerCase(Locale.ROOT)));
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getCounties() { return counties; }
    public void setCounties(String v) { this.counties = v; }
    public int getMaxOpen() { return maxOpen; }
    public void setMaxOpen(int v) { this.maxOpen = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
