package com.kenyarealestate.pms.kafka;

import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.repository.UnitRepository;
import com.kenyarealestate.pms.service.PmsOutboxService;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * These tests are about when the send happens and what key it carries, not about the
 * contents of each event — the seven builders are covered where they are called.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PmsEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private UnitRepository units;
    @Mock private PmsOutboxService outboxService;

    private final UUID outboxId = UUID.randomUUID();

    private PmsEventPublisher publisher() {
        PmsEventPublisher p = new PmsEventPublisher(kafkaTemplate, units, outboxService);
        ReflectionTestUtils.setField(p, "topic", "pms-events");
        when(outboxService.recordPending(any(), any(), any(), any(), any()))
                .thenReturn(PmsOutboxEvent.builder().id(outboxId).build());
        when(units.findById(any())).thenReturn(Optional.empty());
        return p;
    }

    private Lease lease() {
        Lease l = new Lease();
        l.setId(UUID.randomUUID());
        l.setUnitId(UUID.randomUUID());
        l.setTenantId(UUID.randomUUID());
        l.setLandlordId(UUID.randomUUID());
        l.setRentAmount(new BigDecimal("45000.00"));
        l.setBillingDay(1);
        l.setStartDate(LocalDate.now());
        return l;
    }

    private RentInvoice invoice() {
        RentInvoice i = new RentInvoice();
        i.setId(UUID.randomUUID());
        i.setLeaseId(UUID.randomUUID());
        i.setUnitId(UUID.randomUUID());
        i.setTenantId(UUID.randomUUID());
        i.setLandlordId(UUID.randomUUID());
        i.setInvoiceNumber("INV-001");
        i.setAmountDue(new BigDecimal("45000.00"));
        i.setDueDate(LocalDate.now());
        i.setStatus(InvoiceStatus.PENDING);
        return i;
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

    @Nested
    @DisplayName("transaction boundary")
    class Boundary {

        @Test
        @DisplayName("nothing reaches Kafka before the transaction commits")
        void nothingBeforeCommit() {
            TransactionSynchronizationManager.initSynchronization();
            publisher().publishLeaseActivated(lease(), UUID.randomUUID(), null);
            verify(outboxService).recordPending(any(), eq("LEASE_ACTIVATED"), eq("pms-events"), any(), any());
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("a rolled-back transaction never sends — a rent invoice that was not issued is not announced")
        void rollbackSendsNothing() {
            TransactionSynchronizationManager.initSynchronization();
            publisher().publishRentInvoiceIssued(invoice(), UUID.randomUUID());
            // afterCommit is simply never invoked.
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("after commit the send fires")
        void sendsAfterCommit() {
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
            TransactionSynchronizationManager.initSynchronization();
            publisher().publishLeaseActivated(lease(), UUID.randomUUID(), null);
            commit();
            verify(kafkaTemplate).send(any(ProducerRecord.class));
        }
    }

    @Nested
    @DisplayName("partition keys")
    class Keys {

        private String keyRecordedFor(Runnable publish) {
            TransactionSynchronizationManager.initSynchronization();
            publish.run();
            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(outboxService).recordPending(any(), any(), any(), key.capture(), any());
            return key.getValue();
        }

        @Test
        @DisplayName("an overdue notice is keyed by invoice AND day count, so day 7 and day 14 are distinct messages")
        void overdueKeyIncludesDayCount() {
            RentInvoice inv = invoice();
            PmsEventPublisher p = publisher();
            String key = keyRecordedFor(() -> p.publishRentOverdue(inv, 14, UUID.randomUUID()));
            assertThat(key).isEqualTo(inv.getId() + ":14");
        }

        @Test
        @DisplayName("a maintenance resolution is keyed by request AND status, so a reopen-then-resolve is not collapsed")
        void resolutionKeyIncludesStatus() {
            MaintenanceRequest r = new MaintenanceRequest();
            r.setId(UUID.randomUUID());
            r.setUnitId(UUID.randomUUID());
            r.setTenantId(UUID.randomUUID());
            r.setLandlordId(UUID.randomUUID());
            r.setReference("MR-001");
            r.setTitle("Leaking tap");
            r.setStatus(MaintenanceStatus.RESOLVED);
            PmsEventPublisher p = publisher();
            String key = keyRecordedFor(() -> p.publishMaintenanceResolved(r, "A1", UUID.randomUUID()));
            assertThat(key).isEqualTo(r.getId() + ":RESOLVED");
        }

        @Test
        @DisplayName("the key recorded in the outbox is the key the send actually uses, so a retry lands in the same partition")
        void recordedKeyMatchesSentKey() {
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
            RentInvoice inv = invoice();
            TransactionSynchronizationManager.initSynchronization();
            publisher().publishRentOverdue(inv, 7, UUID.randomUUID());
            commit();

            ArgumentCaptor<String> recorded = ArgumentCaptor.forClass(String.class);
            verify(outboxService).recordPending(any(), any(), any(), recorded.capture(), any());
            ArgumentCaptor<ProducerRecord<String, Object>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(sent.capture());

            assertThat(sent.getValue().key()).isEqualTo(recorded.getValue());
        }
    }

    @Nested
    @DisplayName("delivery outcome")
    class Outcome {

        @Test
        @DisplayName("a broker failure marks the attempt failed and leaves the row for the sweeper")
        void failureLeavesRowUnpublished() {
            CompletableFuture<Object> future = new CompletableFuture<>();
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);
            publisher().publishLeaseEnded(lease(), "TERMINATED", null);
            future.completeExceptionally(new RuntimeException("broker down"));

            verify(outboxService).markAttemptFailed(eq(outboxId), contains("broker down"));
            verify(outboxService, never()).markPublished(any());
        }

        @Test
        @DisplayName("a successful send marks the row published so the sweeper leaves it alone")
        void successMarksPublished() {
            CompletableFuture<Object> future = new CompletableFuture<>();
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(i -> future);
            publisher().publishLeaseEnded(lease(), "TERMINATED", null);
            future.complete(null);

            verify(outboxService).markPublished(outboxId);
            verify(outboxService, never()).markAttemptFailed(any(), any());
        }
    }
}
