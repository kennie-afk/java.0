package com.smartseason.notification.domain;

import com.smartseason.notification.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "variables")
    private String variables;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "revision", nullable = false)
    private Integer revision;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }

    public enum Channel { SMS, USSD, PUSH, WHATSAPP, EMAIL }

}
