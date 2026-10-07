package com.kenyarealestate.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kenyarealestate.property.client.VerificationClient;
import com.kenyarealestate.property.dto.PropertyResponse;
import com.kenyarealestate.property.entity.ListingStatus;
import com.kenyarealestate.property.entity.ListingType;
import com.kenyarealestate.property.entity.Property;
import com.kenyarealestate.property.entity.PropertyType;
import com.kenyarealestate.property.exception.NotFoundException;
import com.kenyarealestate.property.kafka.PropertyEventPublisher;
import com.kenyarealestate.property.repository.PropertyImageHashRepository;
import com.kenyarealestate.property.repository.PropertyRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Who may see a listing that is not ACTIVE, on the database path and on the cached path, and who
 * may publish an unlisted one.
 */
class PropertyVisibilityTest {

    private final PropertyRepository repo = mock(PropertyRepository.class);
    private final VerificationClient verifClient = mock(VerificationClient.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> ops = mock(ValueOperations.class);
    private PropertyService service;

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void service() {
        service = new PropertyService(repo, verifClient, redis, mock(PropertyAuditService.class),
                mock(PropertyImageHashRepository.class), mock(ImageHashService.class), mock(PropertyEventPublisher.class));
        ReflectionTestUtils.setField(service, "detailTtl", 300L);
        ReflectionTestUtils.setField(service, "searchTtl", 120L);
        ReflectionTestUtils.setField(service, "viewDebounceWindowMinutes", 30L);
        when(redis.opsForValue()).thenReturn(ops);
    }

    private Property listing(ListingStatus status, List<String> images) {
        return Property.builder().id(id).sellerId(owner).title("Flat").propertyType(PropertyType.APARTMENT)
                .listingType(ListingType.SALE).status(status).county("Nairobi").price(BigDecimal.TEN).viewCount(0)
                .imageUrls(images).build();
    }

    @Test
    void anActiveListingIsPublic() {
        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.ACTIVE, List.of("a.jpg"))));

        assertThat(service.getById(id, null, false, "1.2.3.4").getId()).isEqualTo(id);
    }

    @Test
    void everyOtherStatusIsInvisibleToAnonymousVisitorsAndStrangers() {
        for (ListingStatus hidden : List.of(ListingStatus.DRAFT, ListingStatus.PENDING_VERIFICATION,
                ListingStatus.SUSPENDED, ListingStatus.SOLD, ListingStatus.RENTED, ListingStatus.WITHDRAWN,
                ListingStatus.UNLISTED)) {
            when(repo.findById(id)).thenReturn(Optional.of(listing(hidden, List.of("a.jpg"))));

            assertThatThrownBy(() -> service.getById(id, null, false, "1.2.3.4")).as("anonymous, %s", hidden)
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.getById(id, stranger, false, "1.2.3.4")).as("stranger, %s", hidden)
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void theOwnerAndAnAdminCanStillSeeAHiddenListing() {
        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.UNLISTED, List.of("a.jpg"))));

        assertThat(service.getById(id, owner, false, "1.2.3.4").getStatus()).isEqualTo("UNLISTED");
        assertThat(service.getById(id, stranger, true, "1.2.3.4").getStatus()).isEqualTo("UNLISTED");
    }

    @Test
    void aHiddenListingInTheCacheIsHiddenFromStrangersToo() {
        PropertyResponse cached = PropertyResponse.builder().id(id).sellerId(owner).status("SUSPENDED").build();
        when(ops.get(anyString())).thenReturn(cached);

        assertThatThrownBy(() -> service.getById(id, stranger, false, "1.2.3.4"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getById(id, null, false, "1.2.3.4"))
                .isInstanceOf(NotFoundException.class);
        assertThat(service.getById(id, owner, false, "1.2.3.4")).isSameAs(cached);
        verify(repo, never()).findById(any());
    }

    @Test
    void aListingIsCachedAfterTheFirstDatabaseRead() {
        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.ACTIVE, List.of("a.jpg"))));

        service.getById(id, null, false, "1.2.3.4");

        verify(ops).set(anyString(), any(PropertyResponse.class), eq(Duration.ofSeconds(300)));
    }

    // ---- publish ----------------------------------------------------------------------------

    @Test
    void publishingSomeoneElsesPropertyLooksLikeItDoesNotExist() {
        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.UNLISTED, List.of("a.jpg"))));

        assertThatThrownBy(() -> service.publish(stranger, id)).isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void onlyAnUnlistedPropertyCanBePublishedAndItNeedsAPhoto() {
        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.ACTIVE, List.of("a.jpg"))));
        assertThatThrownBy(() -> service.publish(owner, id)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlisted");

        when(repo.findById(id)).thenReturn(Optional.of(listing(ListingStatus.UNLISTED, List.of())));
        assertThatThrownBy(() -> service.publish(owner, id)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("photo");
        verify(repo, never()).save(any());
    }

    @Test
    void aPublishedPropertyEntersVerificationRatherThanGoingStraightLive() {
        Property p = listing(ListingStatus.UNLISTED, List.of("a.jpg"));
        when(repo.findById(id)).thenReturn(Optional.of(p));
        when(verifClient.isIdentityVerified(owner)).thenReturn(true);

        assertThat(service.publish(owner, id).getStatus()).isEqualTo("PENDING_VERIFICATION");

        Property q = listing(ListingStatus.UNLISTED, List.of("a.jpg"));
        when(repo.findById(id)).thenReturn(Optional.of(q));
        when(verifClient.isIdentityVerified(owner)).thenReturn(false);
        assertThat(service.publish(owner, id).getStatus()).isEqualTo("DRAFT");
    }
}
