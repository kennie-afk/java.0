package com.kenyarealestate.pms.repository;

import com.kenyarealestate.pms.entity.PmsOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PmsOutboxEventRepository extends JpaRepository<PmsOutboxEvent, UUID> {

    List<PmsOutboxEvent> findByPublishedFalseAndCreatedAtBefore(LocalDateTime cutoff);
}
