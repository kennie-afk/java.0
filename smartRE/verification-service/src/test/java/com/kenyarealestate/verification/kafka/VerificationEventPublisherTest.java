package com.kenyarealestate.verification.kafka;

import com.kenyarealestate.verification.entity.VerificationOutboxEvent;
import com.kenyarealestate.verification.service.VerificationOutboxService;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private VerificationOutboxService outboxService;

    private final UUID outboxId = UUID.randomUUID();

    private VerificationEventPublisher publisher() {
        VerificationEventPublisher p = new VerificationEventPublisher(kafkaTemplate, outboxService);
        ReflectionTestUtils.setField(p, "topic", "verification-events");
        when(outboxService.recordPending(any(), any(), any(), any(), any()))
                .thenReturn(VerificationOutboxEvent.builder().id(outboxId).build());
        return p;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("an approval that rolls back is never announced — property-service must not see a verification that was undone")
    void rollbackSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        publisher().publishOwnershipApproved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), "PARCEL/1", "TITLE/1");
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("identity events are keyed by seller")
    void identityKeyedBySeller() {
        UUID sellerId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        publisher().publishIdentityApproved(sellerId, UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now().plusYears(2));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(outboxService).recordPending(any(), eq("IDENTITY_APPROVED"), eq("verification-events"),
                key.capture(), any());
        assertThat(key.getValue()).isEqualTo(sellerId.toString());
    }

    @Test
    @DisplayName("ownership events are keyed by property, not seller — a seller with two properties must not serialise them together")
    void ownershipKeyedByProperty() {
        UUID sellerId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        publisher().publishOwnershipApproved(sellerId, UUID.randomUUID(), propertyId,
                LocalDateTime.now(), "PARCEL/1", "TITLE/1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(outboxService).recordPending(any(), eq("OWNERSHIP_APPROVED"), any(), key.capture(), any());
        assertThat(key.getValue()).isEqualTo(propertyId.toString());
        assertThat(key.getValue()).isNotEqualTo(sellerId.toString());
    }

    @Test
    @DisplayName("after commit the send carries the recorded key and a correlation header")
    void sendsAfterCommitWithRecordedKey() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        UUID propertyId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        publisher().publishOwnershipApproved(UUID.randomUUID(), UUID.randomUUID(), propertyId,
                LocalDateTime.now(), "PARCEL/1", "TITLE/1");
        commit();

        ArgumentCaptor<ProducerRecord<String, Object>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("verification-events");
        assertThat(sent.getValue().key()).isEqualTo(propertyId.toString());
        assertThat(sent.getValue().headers().lastHeader("X-Correlation-Id")).isNotNull();
    }

    @Test
    @DisplayName("a broker failure leaves the row unpublished for the sweeper")
    void failureLeavesRowUnpublished() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);
        publisher().publishIdentityApproved(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now().plusYears(2));
        future.completeExceptionally(new RuntimeException("broker down"));

        verify(outboxService).markAttemptFailed(eq(outboxId), contains("broker down"));
        verify(outboxService, never()).markPublished(any());
    }

    @Test
    @DisplayName("a successful send marks the row published")
    void successMarksPublished() {
        CompletableFuture<Object> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);
        publisher().publishIdentityApproved(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), LocalDateTime.now().plusYears(2));
        future.complete(null);

        verify(outboxService).markPublished(outboxId);
    }
}
