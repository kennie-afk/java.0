package com.kenyarealestate.viewing.kafka;

import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingOutboxEvent;
import com.kenyarealestate.viewing.service.ViewingOutboxService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The publisher's contract is about *when* the Kafka send happens relative to the
 * database transaction, so these tests drive the transaction synchronisation directly
 * rather than trusting that the annotation is enough.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViewingEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ViewingOutboxService outboxService;

    private final UUID outboxId = UUID.randomUUID();

    private ViewingEventPublisher publisher() {
        ViewingEventPublisher p = new ViewingEventPublisher(kafkaTemplate, outboxService);
        ReflectionTestUtils.setField(p, "topic", "viewing-events");
        return p;
    }

    private Viewing viewing() {
        Viewing v = new Viewing();
        v.setId(UUID.randomUUID());
        v.setPropertyId(UUID.randomUUID());
        v.setBuyerId(UUID.randomUUID());
        v.setSellerId(UUID.randomUUID());
        return v;
    }

    private void stubOutboxRecord() {
        when(outboxService.recordPending(any(), any(), any(), any()))
                .thenReturn(ViewingOutboxEvent.builder().id(outboxId).build());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("inside a transaction: the outbox row is written but nothing reaches Kafka before commit")
    void defersSendUntilCommit() {
        stubOutboxRecord();
        TransactionSynchronizationManager.initSynchronization();

        publisher().recordAndPublish(viewing());

        verify(outboxService).recordPending(any(), eq("VIEWING_COMPLETED"), eq("viewing-events"), any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("a rolled-back transaction never sends — the event dies with the row it described")
    void rollbackSendsNothing() {
        stubOutboxRecord();
        TransactionSynchronizationManager.initSynchronization();

        publisher().recordAndPublish(viewing());

        // Nothing calls afterCommit; the synchronisation is simply discarded.
        List<TransactionSynchronization> registered =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("after commit the send fires, keyed by property id so a property's events stay ordered")
    void sendsAfterCommit() {
        stubOutboxRecord();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        TransactionSynchronizationManager.initSynchronization();

        Viewing v = viewing();
        publisher().recordAndPublish(v);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("viewing-events");
        assertThat(captor.getValue().key()).isEqualTo(v.getPropertyId().toString());
        assertThat(captor.getValue().headers().lastHeader("X-Correlation-Id")).isNotNull();
    }

    @Test
    @DisplayName("with no transaction active the send happens immediately — the row is already durable")
    void sendsImmediatelyOutsideTransaction() {
        stubOutboxRecord();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        publisher().recordAndPublish(viewing());

        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("a broker failure marks the attempt failed and leaves the row for the sweeper")
    void brokerFailureLeavesRowUnpublished() {
        stubOutboxRecord();
        CompletableFuture<Object> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);

        publisher().recordAndPublish(viewing());
        future.completeExceptionally(new RuntimeException("broker down"));

        verify(outboxService).markAttemptFailed(eq(outboxId), contains("broker down"));
        verify(outboxService, never()).markPublished(any());
    }

    @Test
    @DisplayName("a successful send marks the row published so the sweeper leaves it alone")
    void successMarksPublished() {
        stubOutboxRecord();
        CompletableFuture<Object> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);

        publisher().recordAndPublish(viewing());
        future.complete(null);

        verify(outboxService).markPublished(outboxId);
        verify(outboxService, never()).markAttemptFailed(any(), any());
    }
}
