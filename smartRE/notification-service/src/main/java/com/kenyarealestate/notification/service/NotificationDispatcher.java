package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.channel.DeliveryChannel;
import com.kenyarealestate.notification.channel.DeliveryException;
import com.kenyarealestate.notification.client.UserServiceClient;
import com.kenyarealestate.notification.dto.UserContactResponse;
import com.kenyarealestate.notification.entity.*;
import com.kenyarealestate.notification.exception.NotFoundException;
import com.kenyarealestate.notification.repository.NotificationPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class NotificationDispatcher {

    private final Map<Channel, DeliveryChannel> channels = new EnumMap<>(Channel.class);
    private final NotificationStore store;
    private final TemplateRenderer renderer;
    private final NotificationPreferenceRepository preferences;
    private final UserServiceClient userClient;
    private final SendRateLimiter rateLimiter;
    private final int maxAttempts;

    public NotificationDispatcher(List<DeliveryChannel> deliveryChannels,
                                  NotificationStore store,
                                  TemplateRenderer renderer,
                                  NotificationPreferenceRepository preferences,
                                  UserServiceClient userClient,
                                  SendRateLimiter rateLimiter,
                                  @Value("${notification.max-attempts:5}") int maxAttempts) {
        deliveryChannels.forEach(c -> this.channels.put(c.type(), c));
        this.store = store;
        this.renderer = renderer;
        this.preferences = preferences;
        this.userClient = userClient;
        this.rateLimiter = rateLimiter;
        this.maxAttempts = maxAttempts;
        log.info("Notification channels active: {}", this.channels.keySet());
    }

    public void dispatch(DispatchCommand cmd) {
        UserContactResponse contact = null;
        if (!StringUtils.hasText(cmd.getRecipientEmail())) {
            contact = userClient.getContact(cmd.getUserId());
        }
        String email = StringUtils.hasText(cmd.getRecipientEmail())
                ? cmd.getRecipientEmail()
                : (contact != null ? contact.getEmail() : null);
        String phone = contact != null ? contact.getPhone() : null;

        Map<String, Object> model = new HashMap<>(cmd.getModel());
        model.putIfAbsent("fullName", contact != null && StringUtils.hasText(contact.getFullName())
                ? contact.getFullName() : "there");

        for (Channel channel : List.of(Channel.EMAIL, Channel.IN_APP)) {
            try {
                dispatchOnChannel(cmd, channel, email, phone, model);
            } catch (NotFoundException e) {
                log.debug("Skipping {} for {}: {}", channel, cmd.getTemplateCode(), e.getMessage());
            } catch (Exception e) {
                log.error("Dispatch failed for templateCode={} channel={} userId={}: {}",
                        cmd.getTemplateCode(), channel, cmd.getUserId(), e.getMessage(), e);
            }
        }
    }

    private void dispatchOnChannel(DispatchCommand cmd, Channel channel,
                                   String email, String phone, Map<String, Object> model) {

        NotificationTemplate template = renderer.findTemplate(cmd.getTemplateCode(), channel);
        Category category = cmd.getCategory() != null ? cmd.getCategory() : template.getCategory();

        String dedupKey = StringUtils.hasText(cmd.getDedupKeyOverride())
                ? DedupKeys.sha256(cmd.getDedupKeyOverride() + ":" + cmd.getUserId() + ":" + channel)
                : DedupKeys.forEvent(
                        String.valueOf(cmd.getSourceEventType()),
                        String.valueOf(cmd.getSourceEventId()),
                        cmd.getUserId(), channel);

        TemplateRenderer.Rendered rendered = renderer.render(template, model);

        Notification n = Notification.builder()
                .userId(cmd.getUserId())
                .recipientEmail(email)
                .recipientPhone(phone)
                .channel(channel)
                .category(category)
                .templateCode(cmd.getTemplateCode())
                .subject(rendered.subject())
                .body(rendered.body())
                .dedupKey(dedupKey)
                .sourceEventType(cmd.getSourceEventType())
                .sourceEventId(cmd.getSourceEventId())
                .entityType(cmd.getEntityType())
                .entityId(cmd.getEntityId())
                .actionUrl(cmd.getActionUrl())
                .status(optedOut(cmd.getUserId(), category, channel)
                        ? NotificationStatus.SUPPRESSED : NotificationStatus.PENDING)
                .build();

        Optional<Notification> saved = store.createIfAbsent(n);
        if (saved.isEmpty()) return;
        Notification row = saved.get();
        if (row.getStatus() == NotificationStatus.SUPPRESSED) return;

        attemptDelivery(row);
    }

    public void attemptDelivery(Notification row) {
        DeliveryChannel channel = channels.get(row.getChannel());

        if (channel == null) {
            store.markSent(row);
            return;
        }

        if (!rateLimiter.tryAcquire()) {
            store.deferBriefly(row);
            return;
        }

        try {
            channel.deliver(row);
            store.markSent(row);
        } catch (DeliveryException e) {
            store.markFailed(row, e.getMessage(), maxAttempts);
        }
    }

    private boolean optedOut(UUID userId, Category category, Channel channel) {
        if (category == Category.ACCOUNT) return false;

        return preferences.findByUserIdAndCategory(userId, category)
                .map(p -> switch (channel) {
                    case EMAIL  -> !p.isEmailEnabled();
                    case SMS    -> !p.isSmsEnabled();
                    case IN_APP -> !p.isInAppEnabled();
                })
                .orElse(false);
    }
}
