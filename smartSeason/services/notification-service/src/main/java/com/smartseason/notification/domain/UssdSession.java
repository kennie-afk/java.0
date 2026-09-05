package com.smartseason.notification.domain;

import com.smartseason.notification.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "ussd_sessions", indexes = {
        @Index(name = "ix_ussd_sessions_session_id", columnList = "session_id"),
        @Index(name = "ix_ussd_sessions_phone_number", columnList = "phone_number")
})
public class UssdSession extends BaseEntity {

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "current_menu", nullable = false)
    private String currentMenu;

    @Column(name = "menu_stack")
    private String menuStack;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context", columnDefinition = "jsonb")
    private String context;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_input_at")
    private Instant lastInputAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "hops", nullable = false)
    private Integer hops;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public String getCurrentMenu() { return currentMenu; }
    public void setCurrentMenu(String currentMenu) { this.currentMenu = currentMenu; }

    public String getMenuStack() { return menuStack; }
    public void setMenuStack(String menuStack) { this.menuStack = menuStack; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getLastInputAt() { return lastInputAt; }
    public void setLastInputAt(Instant lastInputAt) { this.lastInputAt = lastInputAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Integer getHops() { return hops; }
    public void setHops(Integer hops) { this.hops = hops; }

    public enum Status { ACTIVE, COMPLETED, TIMEOUT, ABORTED }

}
