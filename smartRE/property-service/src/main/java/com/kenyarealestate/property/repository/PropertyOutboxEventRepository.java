package com.kenyarealestate.property.repository;

import com.kenyarealestate.property.entity.PropertyOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PropertyOutboxEventRepository extends JpaRepository<PropertyOutboxEvent, UUID> {

    List<PropertyOutboxEvent> findByPublishedFalseAndCreatedAtBefore(LocalDateTime cutoff);
}
