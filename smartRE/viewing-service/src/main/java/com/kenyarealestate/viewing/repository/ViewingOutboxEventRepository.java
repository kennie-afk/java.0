package com.kenyarealestate.viewing.repository;

import com.kenyarealestate.viewing.entity.ViewingOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ViewingOutboxEventRepository extends JpaRepository<ViewingOutboxEvent, UUID> {

    List<ViewingOutboxEvent> findByPublishedFalseAndCreatedAtBefore(LocalDateTime cutoff);
}
