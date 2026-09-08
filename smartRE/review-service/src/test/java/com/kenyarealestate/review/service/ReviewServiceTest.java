package com.kenyarealestate.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kenyarealestate.review.client.PaymentServiceClient;
import com.kenyarealestate.review.dto.CreateReviewRequest;
import com.kenyarealestate.review.entity.Review;
import com.kenyarealestate.review.repository.ReviewAuditLogRepository;
import com.kenyarealestate.review.repository.ReviewRepository;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * A review is the only public signal a buyer has about a stranger they are about to
 * send money to, which makes it the thing most worth faking. Nearly every test here is
 * about somebody trying to write a review they have not earned.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock private ReviewRepository repo;
    @Mock private ReviewAuditLogRepository auditRepo;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private PaymentServiceClient paymentServiceClient;

    private ReviewService service;

    private final UUID reviewerId = UUID.randomUUID();
    private final UUID sellerId   = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID paymentId  = UUID.randomUUID();
    private final String ip       = "196.201.0.9";

    @BeforeEach
    void setUp() {
        service = new ReviewService(repo, auditRepo, redis, paymentServiceClient);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(repo.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
    }

    @Nested
    @DisplayName("earning the right to review")
    class Eligibility {

        @Test
        @DisplayName("a payment that matches buyer, seller and property lets the review through")
        void acceptsAGenuineReview() {
            when(valueOps.get(anyString()))
                    .thenReturn(reviewerId + "|" + sellerId + "|" + propertyId);

            var res = service.create(reviewerId, request(5), ip);

            assertEquals(5, res.getRating());
            verify(repo).save(any(Review.class));
        }

        @Test
        @DisplayName("a payment for a different property cannot be used to review this one")
        void rejectsAPaymentForAnotherProperty() {
            // The attack this blocks: pay for one cheap viewing, then use that receipt
            // to post reviews across every listing a seller has.
            when(valueOps.get(anyString()))
                    .thenReturn(reviewerId + "|" + sellerId + "|" + UUID.randomUUID());

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(1), ip));
            verify(repo, never()).save(any(Review.class));
        }

        @Test
        @DisplayName("somebody else's payment cannot be used to review")
        void rejectsAPaymentBelongingToAnotherBuyer() {
            when(valueOps.get(anyString()))
                    .thenReturn(UUID.randomUUID() + "|" + sellerId + "|" + propertyId);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
        }

        @Test
        @DisplayName("a rejected attempt is recorded, not silently dropped")
        void auditsTheRejection() {
            // A pattern of these against one seller is itself the signal.
            when(valueOps.get(anyString()))
                    .thenReturn(UUID.randomUUID() + "|" + sellerId + "|" + propertyId);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(1), ip));
            verify(auditRepo, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("a cache miss falls back to asking payment-service")
        void fallsBackToThePaymentServiceWhenTheCacheIsCold() {
            // Redis is a speed-up, never the authority. A cold cache must not become
            // an open door.
            when(valueOps.get(anyString())).thenReturn(null);
            when(paymentServiceClient.checkEligible(paymentId, reviewerId, sellerId, propertyId))
                    .thenReturn(true);

            assertDoesNotThrow(() -> service.create(reviewerId, request(4), ip));
            verify(paymentServiceClient).checkEligible(paymentId, reviewerId, sellerId, propertyId);
        }

        @Test
        @DisplayName("a cache miss with no matching payment is refused")
        void refusesWhenThePaymentServiceSaysNo() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(paymentServiceClient.checkEligible(any(), any(), any(), any())).thenReturn(false);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
        }

        @Test
        @DisplayName("a malformed cache entry is not trusted")
        void ignoresAGarbledCacheValue() {
            // Anything that is not exactly three fields falls through to the authority
            // rather than being parsed optimistically.
            when(valueOps.get(anyString())).thenReturn("not|three-fields");
            when(paymentServiceClient.checkEligible(any(), any(), any(), any())).thenReturn(false);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
            verify(paymentServiceClient).checkEligible(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("one review per thing")
    class Duplicates {

        @Test
        void aPaymentCanOnlyBeSpentOnce() {
            when(repo.existsByPaymentId(paymentId)).thenReturn(true);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
            verify(repo, never()).save(any(Review.class));
        }

        @Test
        void aBuyerCannotReviewTheSamePropertyTwice() {
            when(repo.existsByReviewerIdAndPropertyId(reviewerId, propertyId)).thenReturn(true);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
        }

        @Test
        @DisplayName("duplication is checked before eligibility")
        void doesNotCallThePaymentServiceForAKnownDuplicate() {
            // Cheap local check first; no need to trouble another service to refuse.
            when(repo.existsByPaymentId(paymentId)).thenReturn(true);

            assertThrows(RuntimeException.class, () -> service.create(reviewerId, request(5), ip));
            verifyNoInteractions(paymentServiceClient);
        }
    }

    @Nested
    @DisplayName("moderation and ratings")
    class Moderation {

        @Test
        void hidingAReviewKeepsItButUnverifiesIt() {
            // Hidden, not deleted: the audit trail and the original text survive.
            Review r = Review.builder().id(UUID.randomUUID()).sellerId(sellerId)
                    .reviewerId(reviewerId).propertyId(propertyId).rating(1)
                    .comment("spam").isVerified(true).build();
            when(repo.findById(r.getId())).thenReturn(Optional.of(r));

            var res = service.adminHide(r.getId(), UUID.randomUUID(), "spam");

            assertFalse(res.isVerified());
            verify(repo).save(any(Review.class));
            verify(auditRepo, atLeastOnce()).save(any());
        }

        @Test
        void aSellerWithNoReviewsRatesZeroRatherThanNull() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(repo.avgRating(sellerId)).thenReturn(null);
            when(repo.countBySeller(sellerId)).thenReturn(0L);

            var rating = service.getSellerRating(sellerId);

            assertEquals(0.0, rating.getAverageRating());
            assertEquals(0L, rating.getReviewCount());
        }

        @Test
        void ratingsAreRoundedToOneDecimal() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(repo.avgRating(sellerId)).thenReturn(4.26666);
            when(repo.countBySeller(sellerId)).thenReturn(3L);

            assertEquals(4.3, service.getSellerRating(sellerId).getAverageRating());
        }

        @Test
        @DisplayName("a broken cache degrades to the database rather than failing")
        void survivesRedisBeingDown() {
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
            when(repo.avgRating(sellerId)).thenReturn(4.0);
            when(repo.countBySeller(sellerId)).thenReturn(2L);

            assertEquals(4.0, service.getSellerRating(sellerId).getAverageRating());
        }
    }

    private CreateReviewRequest request(int rating) {
        var req = new CreateReviewRequest();
        req.setSellerId(sellerId);
        req.setPropertyId(propertyId);
        req.setPaymentId(paymentId);
        req.setRating(rating);
        req.setComment("Straightforward viewing, seller was on time.");
        return req;
    }
}
