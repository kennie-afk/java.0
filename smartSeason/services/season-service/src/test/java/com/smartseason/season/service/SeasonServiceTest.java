package com.smartseason.season.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartseason.season.domain.Season;
import com.smartseason.season.domain.SeasonStage;
import com.smartseason.season.platform.DomainRuleException;
import com.smartseason.season.repo.SeasonStageRepository;
import com.smartseason.season.repo.StageTemplateRepository;
import com.smartseason.season.web.dto.SeasonUpdateRequest;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import static org.mockito.Mockito.times;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.CountCache;
import com.smartseason.season.platform.EventPublisher;
import com.smartseason.season.platform.ReferenceChecker;
import com.smartseason.season.platform.ResourceNotFoundException;
import com.smartseason.season.platform.TenantContext;
import com.smartseason.season.platform.TenantMissingException;
import com.smartseason.season.repo.SeasonRepository;
import com.smartseason.season.web.dto.SeasonCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeasonServiceTest {

    private final SeasonRepository repository = mock(SeasonRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final CountCache counts = new CountCache(null, 30, false);

    private final SeasonStageRepository stageRepository = mock(SeasonStageRepository.class);
    private final StageTemplateRepository templateRepository = mock(StageTemplateRepository.class);

    private final SeasonService service = new SeasonService(repository, events, counts,
            ReferenceChecker.disabled(), stageRepository, templateRepository);

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {
        TenantContext.set(tenant);
        when(templateRepository.findAllByCropCodeAndTenantId(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(repository.save(any(Season.class))).thenAnswer(invocation -> {
            Season saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
    }

    private static final java.time.ZoneId NAIROBI = java.time.ZoneId.of("Africa/Nairobi");

    private SeasonCreateRequest start(String crop, LocalDate startDate) {
        return new SeasonCreateRequest(UUID.randomUUID(), null, crop, null, startDate,
                null, null, null, null, null, null);
    }

    private List<SeasonStage> savedStages(int expected) {
        ArgumentCaptor<SeasonStage> captor = ArgumentCaptor.forClass(SeasonStage.class);
        verify(stageRepository, times(expected)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("starting maize today sets status, harvest date and a five-stage calendar")
    void startingASeasonBuildsItsCalendar() {
        LocalDate today = LocalDate.now(NAIROBI);
        var response = service.create(start("maize", today));

        assertThat(response.status()).isEqualTo(Season.Status.ACTIVE);
        assertThat(response.cropCode()).isEqualTo("MAIZE");
        assertThat(response.expectedHarvestDate()).isEqualTo(today.plusDays(150));
        assertThat(response.currentStage()).isEqualTo("EMERGENCE");
        List<SeasonStage> stages = savedStages(5);
        assertThat(stages.get(0).getStatus()).isEqualTo(SeasonStage.Status.ACTIVE);
        assertThat(stages.get(4).getPlannedEnd()).isEqualTo(today.plusDays(150));
        assertThat(stages.subList(1, 5)).extracting(SeasonStage::getStatus)
                .containsOnly(SeasonStage.Status.PENDING);
    }

    @Test
    @DisplayName("a season starting in the future is Planned and its stages wait")
    void futureStartIsPlanned() {
        var response = service.create(start("beans", LocalDate.now(NAIROBI).plusDays(10)));
        assertThat(response.status()).isEqualTo(Season.Status.PLANNED);
        assertThat(savedStages(5)).extracting(SeasonStage::getStatus)
                .containsOnly(SeasonStage.Status.PENDING);
    }

    @Test
    @DisplayName("a season already under way lands on the stage the calendar says it is in")
    void lateEntryFindsItsStage() {
        var response = service.create(start("maize", LocalDate.now(NAIROBI).minusDays(60)));
        // Emergence 0-10 and Vegetative 10-60 are over; Tasseling 60-81 is now.
        assertThat(response.currentStage()).isEqualTo("TASSELING");
        List<SeasonStage> stages = savedStages(5);
        assertThat(stages.get(0).getStatus()).isEqualTo(SeasonStage.Status.COMPLETE);
        assertThat(stages.get(1).getStatus()).isEqualTo(SeasonStage.Status.COMPLETE);
        assertThat(stages.get(2).getStatus()).isEqualTo(SeasonStage.Status.ACTIVE);
    }

    @Test
    @DisplayName("harvest results cannot be supplied when a season starts")
    void actualsRejectedAtStart() {
        var bad = new SeasonCreateRequest(UUID.randomUUID(), null, "MAIZE", null, LocalDate.now(NAIROBI),
                null, LocalDate.now(NAIROBI), null, new BigDecimal("400"), null, null);
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("recorded when the season is harvested");
    }

    @Test
    @DisplayName("a crop with no calendar is created without stages")
    void unknownCropHasNoStages() {
        service.create(start("avocado", LocalDate.now(NAIROBI)));
        verify(stageRepository, times(0)).save(any(SeasonStage.class));
    }

    private Season existing(Season.Status status) {
        Season season = new Season();
        season.setId(UUID.randomUUID());
        season.setTenantId(tenant);
        season.setCropCode("MAIZE");
        season.setStartDate(LocalDate.now(NAIROBI).minusDays(100));
        season.setStatus(status);
        when(repository.findByIdAndTenantId(season.getId(), tenant)).thenReturn(Optional.of(season));
        return season;
    }

    private static SeasonUpdateRequest statusOnly(Season.Status status) {
        return new SeasonUpdateRequest(null, null, null, null, null, null, null, null, null, null, status);
    }

    @Test
    @DisplayName("a season cannot skip from Active to Closed")
    void cannotSkipStatuses() {
        Season season = existing(Season.Status.ACTIVE);
        assertThatThrownBy(() -> service.update(season.getId(), statusOnly(Season.Status.CLOSED)))
                .isInstanceOf(DomainRuleException.class).hasMessageContaining("cannot go from ACTIVE to CLOSED");
    }

    @Test
    @DisplayName("harvesting needs a recorded yield, and stamps today as the harvest date")
    void harvestNeedsYield() {
        Season season = existing(Season.Status.ACTIVE);
        assertThatThrownBy(() -> service.update(season.getId(), statusOnly(Season.Status.HARVESTED)))
                .isInstanceOf(DomainRuleException.class).hasMessageContaining("actual yield");

        season.setActualYieldKg(new BigDecimal("1800"));
        var done = service.update(season.getId(), statusOnly(Season.Status.HARVESTED));
        assertThat(done.status()).isEqualTo(Season.Status.HARVESTED);
        assertThat(done.actualHarvestDate()).isEqualTo(LocalDate.now(NAIROBI));
    }


    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create persists the entity against the caller's tenant and emits an event")
    void createStampsTenantAndPublishes() {
        when(repository.save(any(Season.class))).thenAnswer(invocation -> {
            Season saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        var response = service.create(new SeasonCreateRequest(UUID.randomUUID(), null, "test", null, LocalDate.now(), null, null, null, null, null, Season.Status.PLANNED));

        assertThat(response.id()).isNotNull();
        verify(events).publish(any(), eq("SeasonCreated"), any(), any());
    }

    @Test
    @DisplayName("a row belonging to another tenant reads as not found")
    void otherTenantRowIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("an unbound tenant fails closed instead of querying across tenants")
    void missingTenantFailsClosed() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(TenantMissingException.class);
    }
}
