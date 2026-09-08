package com.kenyarealestate.viewing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kenyarealestate.viewing.client.PaymentClient;
import com.kenyarealestate.viewing.client.PropertyClient;
import com.kenyarealestate.viewing.dto.ScheduleViewingRequest;
import com.kenyarealestate.viewing.entity.Viewing;
import com.kenyarealestate.viewing.entity.ViewingStatus;
import com.kenyarealestate.viewing.exception.ConflictException;
import com.kenyarealestate.viewing.exception.ForbiddenException;
import com.kenyarealestate.viewing.exception.NotFoundException;
import com.kenyarealestate.viewing.kafka.ViewingEventPublisher;
import com.kenyarealestate.viewing.repository.ViewingAuditLogRepository;
import com.kenyarealestate.viewing.repository.ViewingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A viewing is the moment a buyer and a stranger agree to meet at a property, and the
 * platform takes a fee for arranging it. These tests are about the rules that protect
 * both sides of that: who may act, what order things may happen in, and what the money
 * does when an arrangement falls through.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViewingServiceTest {

    @Mock private ViewingRepository repo;
    @Mock private ViewingEventPublisher eventPublisher;
    @Mock private PaymentClient paymentClient;
    @Mock private PropertyClient propertyClient;
    @Mock private ViewingAuditLogRepository auditRepo;

    private ViewingService service;

    private final UUID buyerId    = UUID.randomUUID();
    private final UUID sellerId   = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID viewingId  = UUID.randomUUID();
    private final String ip       = "196.201.0.1";

    @BeforeEach
    void setUp() {
        service = new ViewingService(repo, eventPublisher, paymentClient, propertyClient, auditRepo);
        when(repo.save(any(Viewing.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.saveAndFlush(any(Viewing.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("scheduling")
    class Scheduling {

        @Test
        @DisplayName("an unverified seller cannot be booked with")
        void refusesUnverifiedSeller() {
            // The whole premise of the platform is that you know who you are meeting.
            when(propertyClient.isSellerIdentityVerified(propertyId)).thenReturn(false);

            RuntimeException e = assertThrows(RuntimeException.class,
                    () -> service.schedule(buyerId, request(), ip));

            assertTrue(e.getMessage().contains("identity verification"));
            verify(repo, never()).save(any());
        }

        @Test
        @DisplayName("the seller on the request must own the property")
        void refusesWhenSellerDoesNotOwnTheProperty() {
            // Otherwise a third party could insert themselves as the seller and take
            // the meeting — and the fee.
            when(propertyClient.isSellerIdentityVerified(propertyId)).thenReturn(true);
            when(propertyClient.getSellerId(propertyId)).thenReturn(UUID.randomUUID());

            assertThrows(ForbiddenException.class, () -> service.schedule(buyerId, request(), ip));
            verify(repo, never()).save(any());
        }

        @Test
        @DisplayName("a buyer cannot double-book the same property")
        void refusesASecondActiveViewing() {
            allowScheduling();
            when(repo.findFirstByPropertyIdAndBuyerIdAndStatusInOrderByCreatedAtDesc(
                    eq(propertyId), eq(buyerId), anyList()))
                    .thenReturn(Optional.of(viewing(ViewingStatus.CONFIRMED)));

            assertThrows(ConflictException.class, () -> service.schedule(buyerId, request(), ip));
        }

        @Test
        @DisplayName("a database-level double-booking surfaces as a conflict, not a 500")
        void translatesUniqueViolationToConflict() {
            // The application check above races; the unique index is what actually
            // holds. A buyer who loses that race deserves an explanation, not a crash.
            allowScheduling();
            when(repo.save(any(Viewing.class))).thenThrow(new DataIntegrityViolationException("dup"));

            assertThrows(ConflictException.class, () -> service.schedule(buyerId, request(), ip));
        }

        @Test
        @DisplayName("a new viewing waits for the fee before anything else")
        void startsInPendingFee() {
            allowScheduling();
            when(paymentClient.initiateViewingFee(any(), any(), any(), any(), any())).thenReturn(null);

            var res = service.schedule(buyerId, request(), ip);

            assertEquals(ViewingStatus.PENDING_FEE.name(), res.getStatus());
        }

        @Test
        @DisplayName("a failed fee initiation still leaves an auditable viewing")
        void survivesPaymentServiceBeingDown() {
            // Losing the STK push must not lose the booking. The buyer can retry; a
            // silently dropped viewing would just look like the site is broken.
            allowScheduling();
            when(paymentClient.initiateViewingFee(any(), any(), any(), any(), any())).thenReturn(null);

            assertDoesNotThrow(() -> service.schedule(buyerId, request(), ip));
            verify(auditRepo, atLeastOnce()).save(any());
        }
    }

    @Nested
    @DisplayName("confirmation needs both sides")
    class Confirmation {

        @Test
        void oneSideAloneDoesNotConfirm() {
            Viewing v = viewing(ViewingStatus.REQUESTED);
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            var res = service.confirmSeller(viewingId, sellerId, ip);

            assertEquals(ViewingStatus.REQUESTED.name(), res.getStatus());
        }

        @Test
        void bothSidesConfirmingMovesItToConfirmed() {
            Viewing v = viewing(ViewingStatus.REQUESTED);
            v.setBuyerConfirmed(true);
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            var res = service.confirmSeller(viewingId, sellerId, ip);

            assertEquals(ViewingStatus.CONFIRMED.name(), res.getStatus());
        }

        @Test
        @DisplayName("nobody can confirm before the fee is paid")
        void refusesConfirmationWhilePendingFee() {
            when(repo.findById(viewingId)).thenReturn(Optional.of(viewing(ViewingStatus.PENDING_FEE)));

            assertThrows(RuntimeException.class, () -> service.confirmSeller(viewingId, sellerId, ip));
            assertThrows(RuntimeException.class, () -> service.confirmBuyer(viewingId, buyerId, ip));
        }

        @Test
        @DisplayName("a stranger cannot confirm somebody else's viewing")
        void refusesAnUnrelatedUser() {
            when(repo.findById(viewingId)).thenReturn(Optional.of(viewing(ViewingStatus.REQUESTED)));

            assertThrows(ForbiddenException.class,
                    () -> service.confirmSeller(viewingId, UUID.randomUUID(), ip));
            assertThrows(ForbiddenException.class,
                    () -> service.confirmBuyer(viewingId, UUID.randomUUID(), ip));
        }

        @Test
        void aCancelledViewingCannotBeConfirmed() {
            when(repo.findById(viewingId)).thenReturn(Optional.of(viewing(ViewingStatus.CANCELLED)));

            assertThrows(RuntimeException.class, () -> service.confirmSeller(viewingId, sellerId, ip));
        }
    }

    @Nested
    @DisplayName("completion")
    class Completion {

        @Test
        @DisplayName("cannot be completed before the appointment has happened")
        void refusesCompletionBeforeTheScheduledTime() {
            // Otherwise a seller could mark a future viewing complete to unlock the
            // review it entitles the buyer to write.
            Viewing v = viewing(ViewingStatus.CONFIRMED);
            v.setBuyerConfirmed(true);
            v.setSellerConfirmed(true);
            v.setScheduledAt(LocalDateTime.now().plusDays(1));
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            assertThrows(RuntimeException.class, () -> service.markCompleted(viewingId, sellerId, false));
        }

        @Test
        void refusesCompletionUnlessBothConfirmed() {
            Viewing v = viewing(ViewingStatus.REQUESTED);
            v.setBuyerConfirmed(true);
            v.setScheduledAt(LocalDateTime.now().minusHours(1));
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            assertThrows(RuntimeException.class, () -> service.markCompleted(viewingId, buyerId, false));
        }

        @Test
        void completesAndAnnouncesIt() {
            // The published event is what lets review-service know a review is earned.
            Viewing v = readyToComplete();
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            var res = service.markCompleted(viewingId, buyerId, false);

            assertEquals(ViewingStatus.COMPLETED.name(), res.getStatus());
            verify(eventPublisher).recordAndPublish(any(Viewing.class));
        }

        @Test
        void onlyTheTwoPartiesOrAnAdminMayComplete() {
            when(repo.findById(viewingId)).thenReturn(Optional.of(readyToComplete()));

            assertThrows(ForbiddenException.class,
                    () -> service.markCompleted(viewingId, UUID.randomUUID(), false));
            assertDoesNotThrow(() -> service.markCompleted(viewingId, UUID.randomUUID(), true));
        }

        @Test
        void cannotCompleteTwice() {
            Viewing v = readyToComplete();
            v.setStatus(ViewingStatus.COMPLETED);
            when(repo.findById(viewingId)).thenReturn(Optional.of(v));

            assertThrows(RuntimeException.class, () -> service.markCompleted(viewingId, buyerId, false));
        }
    }

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        void reportsWhetherABuyerHasActuallyViewedAProperty() {
            // review-service leans on this: no viewing, no review.
            when(repo.existsByPropertyIdAndBuyerIdAndStatus(propertyId, buyerId, ViewingStatus.COMPLETED))
                    .thenReturn(true);

            assertTrue(service.hasCompletedViewing(propertyId, buyerId));
        }

        @Test
        void anUnknownViewingIsNotFound() {
            when(repo.findById(viewingId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> service.confirmBuyer(viewingId, buyerId, ip));
        }
    }

    // ------------------------------------------------------------------ helpers ---

    private void allowScheduling() {
        when(propertyClient.isSellerIdentityVerified(propertyId)).thenReturn(true);
        when(propertyClient.getSellerId(propertyId)).thenReturn(sellerId);
        when(repo.findFirstByPropertyIdAndBuyerIdAndStatusInOrderByCreatedAtDesc(
                eq(propertyId), eq(buyerId), anyList())).thenReturn(Optional.empty());
    }

    private ScheduleViewingRequest request() {
        var req = new ScheduleViewingRequest();
        req.setPropertyId(propertyId);
        req.setSellerId(sellerId);
        req.setScheduledAt(LocalDateTime.now().plusDays(2));
        req.setBuyerPhone("+254722000111");
        req.setNotes("Afternoon if possible");
        return req;
    }

    private Viewing viewing(ViewingStatus status) {
        return Viewing.builder()
                .id(viewingId)
                .propertyId(propertyId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .status(status)
                .scheduledAt(LocalDateTime.now().plusDays(2))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private Viewing readyToComplete() {
        Viewing v = viewing(ViewingStatus.CONFIRMED);
        v.setBuyerConfirmed(true);
        v.setSellerConfirmed(true);
        v.setScheduledAt(LocalDateTime.now().minusHours(2));
        return v;
    }
}
