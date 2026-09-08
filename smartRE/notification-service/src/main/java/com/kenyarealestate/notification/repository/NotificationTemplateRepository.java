package com.kenyarealestate.notification.repository;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByCodeAndChannelAndLocaleAndActiveTrue(String code, Channel channel, String locale);

    /** Every active template on one channel, used to work out which categories it can serve. */
    List<NotificationTemplate> findByChannelAndActiveTrue(Channel channel);
}
