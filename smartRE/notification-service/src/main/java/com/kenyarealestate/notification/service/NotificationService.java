package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.dto.*;
import com.kenyarealestate.notification.entity.*;
import com.kenyarealestate.notification.exception.ForbiddenException;
import com.kenyarealestate.notification.exception.NotFoundException;
import com.kenyarealestate.notification.repository.NotificationPreferenceRepository;
import com.kenyarealestate.notification.repository.NotificationRepository;
import com.kenyarealestate.notification.repository.NotificationTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDispatcher dispatcher;
    private final NotificationTemplateRepository templates;

    public NotificationService(NotificationRepository repo,
                               NotificationPreferenceRepository preferences,
                               NotificationDispatcher dispatcher,
                               NotificationTemplateRepository templates) {
        this.repo = repo;
        this.preferences = preferences;
        this.dispatcher = dispatcher;
        this.templates = templates;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> feed(UUID userId, Pageable pageable) {
        return repo.findByUserIdAndChannelOrderByCreatedAtDesc(userId, Channel.IN_APP, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return UnreadCountResponse.builder()
                .unread(repo.countByUserIdAndChannelAndReadAtIsNull(userId, Channel.IN_APP))
                .build();
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification n = repo.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new ForbiddenException("Not your notification");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            repo.save(n);
        }
        return toResponse(n);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repo.markAllRead(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> getPreferences(UUID userId) {
        Map<Category, NotificationPreference> stored = new EnumMap<>(Category.class);
        preferences.findByUserId(userId).forEach(p -> stored.put(p.getCategory(), p));

        // Which channels this category can actually deliver on, read from the templates
        // that exist rather than hardcoded, so adding an SMS template is all it takes for
        // the switch to become live.
        Set<Category> withEmail = channelsWithTemplates(Channel.EMAIL);
        Set<Category> withSms   = channelsWithTemplates(Channel.SMS);
        Set<Category> withInApp = channelsWithTemplates(Channel.IN_APP);

        List<PreferenceResponse> out = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c == Category.ACCOUNT) continue;
            NotificationPreference p = stored.get(c);
            out.add(PreferenceResponse.builder()
                    .category(c.name())
                    .emailEnabled(p == null || p.isEmailEnabled())
                    .smsEnabled(p == null || p.isSmsEnabled())
                    .inAppEnabled(p == null || p.isInAppEnabled())
                    .emailAvailable(withEmail.contains(c))
                    .smsAvailable(withSms.contains(c))
                    .inAppAvailable(withInApp.contains(c))
                    .build());
        }
        return out;
    }

    private Set<Category> channelsWithTemplates(Channel channel) {
        return templates.findByChannelAndActiveTrue(channel).stream()
                .map(NotificationTemplate::getCategory)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Transactional
    public PreferenceResponse updatePreference(UUID userId, UpdatePreferenceRequest req) {
        Category category;
        try {
            category = Category.valueOf(req.getCategory().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unknown notification category: " + req.getCategory());
        }
        if (category == Category.ACCOUNT) {
            throw new ForbiddenException(
                    "Account notifications cannot be turned off — they include password resets.");
        }

        NotificationPreference p = preferences.findByUserIdAndCategory(userId, category)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId).category(category).build());

        if (req.getEmailEnabled() != null) p.setEmailEnabled(req.getEmailEnabled());
        if (req.getSmsEnabled()   != null) p.setSmsEnabled(req.getSmsEnabled());
        if (req.getInAppEnabled() != null) p.setInAppEnabled(req.getInAppEnabled());
        preferences.save(p);

        return PreferenceResponse.builder()
                .category(category.name())
                .emailEnabled(p.isEmailEnabled())
                .smsEnabled(p.isSmsEnabled())
                .inAppEnabled(p.isInAppEnabled())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> adminList(NotificationStatus status, Pageable pageable) {
        Page<Notification> page = status == null
                ? repo.findAllByOrderByCreatedAtDesc(pageable)
                : repo.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(this::toAdminResponse);
    }

    @Transactional
    public AdminNotificationResponse retry(UUID notificationId) {
        Notification n = repo.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.setAttempts(0);
        n.setNextAttemptAt(null);
        n.setStatus(NotificationStatus.PENDING);
        repo.save(n);
        dispatcher.attemptDelivery(n);
        return toAdminResponse(n);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .category(n.getCategory().name())
                .templateCode(n.getTemplateCode())
                .subject(n.getSubject())
                .body(n.getBody())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .actionUrl(n.getActionUrl())
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }

    private AdminNotificationResponse toAdminResponse(Notification n) {
        return AdminNotificationResponse.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .recipientEmail(n.getRecipientEmail())
                .channel(n.getChannel().name())
                .category(n.getCategory().name())
                .templateCode(n.getTemplateCode())
                .subject(n.getSubject())
                .status(n.getStatus().name())
                .attempts(n.getAttempts())
                .lastError(n.getLastError())
                .sourceEventType(n.getSourceEventType())
                .sourceEventId(n.getSourceEventId())
                .createdAt(n.getCreatedAt())
                .nextAttemptAt(n.getNextAttemptAt())
                .sentAt(n.getSentAt())
                .build();
    }
}
