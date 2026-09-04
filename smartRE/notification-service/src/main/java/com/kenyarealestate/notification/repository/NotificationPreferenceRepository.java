package com.kenyarealestate.notification.repository;

import com.kenyarealestate.notification.entity.Category;
import com.kenyarealestate.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserIdAndCategory(UUID userId, Category category);
    List<NotificationPreference> findByUserId(UUID userId);
}
