package com.kenyarealestate.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kenyarealestate.review.client.PaymentServiceClient;
import com.kenyarealestate.review.dto.ReviewAdminStatsResponse;
import com.kenyarealestate.review.dto.ReviewResponse;
import com.kenyarealestate.review.entity.Review;
import com.kenyarealestate.review.entity.ReviewAuditLog;
import com.kenyarealestate.review.repository.ReviewAuditLogRepository;
import com.kenyarealestate.review.repository.ReviewRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Hiding a review: what it changes, what it must not change, and what it leaves behind.
 * A hidden review stays in the database and in the audit trail; only the public number moves.
 */
class ReviewModerationTest {

    private final ReviewRepository repo = mock(ReviewRepository.class);
    private final ReviewAuditLogRepository audit = mock(ReviewAuditLogRepository.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private ReviewService service;

    private final UUID admin = UUID.randomUUID();
    private final UUID seller = UUID.randomUUID();
    private Review review;

    @BeforeEach
    void setUp() {
        service = new ReviewService(repo, audit, redis, mock(PaymentServiceClient.class));
        review = Review.builder().id(UUID.randomUUID()).reviewerId(UUID.randomUUID()).sellerId(seller)
                .propertyId(UUID.randomUUID()).paymentId(UUID.randomUUID()).rating(1).comment("rude").build();
        when(repo.findById(review.getId())).thenReturn(Optional.of(review));
        when(repo.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void hidingKeepsTheReviewButTakesItOutOfThePublicSet() {
        assertTrue(review.isVerified());

        ReviewResponse response = service.adminHide(review.getId(), admin, "Fake");

        assertFalse(response.isVerified());
        assertFalse(review.isVerified());
        verify(repo).save(review);
        verify(repo, never()).delete(any(Review.class));
        verify(repo, never()).deleteById(any());
        assertEquals("rude", response.getComment());
    }

    @Test
    void hidingDropsTheSellersCachedRatingSoTheScoreMovesAtOnce() {
        service.adminHide(review.getId(), admin, "Fake");

        verify(redis).delete("rating:" + seller);
    }

    @Test
    void hidingIsAuditedWithWhoDidItAndWhy() {
        service.adminHide(review.getId(), admin, "Reported as fake or abusive");

        ArgumentCaptor<ReviewAuditLog> logged = ArgumentCaptor.forClass(ReviewAuditLog.class);
        verify(audit).save(logged.capture());
        assertEquals("REVIEW_HIDDEN_BY_ADMIN", logged.getValue().getEventType());
        assertEquals(admin, logged.getValue().getActorId());
        assertEquals(review.getId(), logged.getValue().getReviewId());
        assertTrue(logged.getValue().getDetail().contains("Reported as fake or abusive"));
    }

    @Test
    void hidingAReviewThatDoesNotExistChangesNothing() {
        UUID missing = UUID.randomUUID();
        when(repo.findById(missing)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.adminHide(missing, admin, "x"));

        verify(repo, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void aFailingAuditWriteDoesNotUndoTheHide() {
        when(audit.save(any())).thenThrow(new IllegalStateException("audit db down"));

        ReviewResponse response = service.adminHide(review.getId(), admin, "Fake");

        assertFalse(response.isVerified());
    }

    @Test
    void publicListsAskOnlyForVerifiedReviews() {
        Pageable page = PageRequest.of(0, 20);
        UUID property = UUID.randomUUID();
        when(repo.findByPropertyIdAndIsVerifiedTrue(eq(property), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(repo.findBySellerIdAndIsVerifiedTrue(eq(seller), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.getByProperty(property, page);
        service.getBySeller(seller, page);

        verify(repo).findByPropertyIdAndIsVerifiedTrue(property, page);
        verify(repo).findBySellerIdAndIsVerifiedTrue(seller, page);
        verify(repo, never()).findAll(any(Pageable.class));
    }

    @Test
    void anAdminSeesHiddenAndVisibleOrJustOneKind() {
        Pageable page = PageRequest.of(0, 20);
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(review)));
        when(repo.findByIsVerified(anyBoolean(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertEquals(1, service.adminGetAll(null, page).getTotalElements());
        service.adminGetAll(false, page);
        service.adminGetAll(true, page);

        verify(repo).findAll(page);
        verify(repo).findByIsVerified(false, page);
        verify(repo).findByIsVerified(true, page);
    }

    @Test
    void aReviewersOwnListIsScopedToThem() {
        UUID reviewer = UUID.randomUUID();
        Pageable page = PageRequest.of(0, 20);
        when(repo.findByReviewerId(eq(reviewer), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(review)));

        service.getByReviewer(reviewer, page);

        verify(repo).findByReviewerId(reviewer, page);
    }

    @Test
    void theStatsCountHiddenReviewsSeparatelyAndRoundTheAverage() {
        when(repo.countByIsVerifiedTrue()).thenReturn(8L);
        when(repo.countByIsVerifiedFalse()).thenReturn(2L);
        when(repo.avgRatingPlatform()).thenReturn(4.2666);
        when(repo.ratingDistribution()).thenReturn(List.<Object[]>of(new Object[] {5, 6L}, new Object[] {4, 2L}));

        ReviewAdminStatsResponse stats = service.getAdminStats();

        assertEquals(10, stats.getTotalReviews());
        assertEquals(8, stats.getVisibleReviews());
        assertEquals(2, stats.getHiddenReviews());
        assertEquals(4.3, stats.getAverageRating());
        assertEquals(2, stats.getRatingDistribution().size());
        assertEquals(5, stats.getRatingDistribution().get(0).getRating());
    }

    @Test
    void anEmptyPlatformAveragesZeroNotNull() {
        when(repo.avgRatingPlatform()).thenReturn(null);
        when(repo.ratingDistribution()).thenReturn(List.of());

        assertEquals(0.0, service.getAdminStats().getAverageRating());
    }
}
