package com.smartseason.media.mymedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartseason.media.domain.MediaAsset;
import com.smartseason.media.platform.ResourceNotFoundException;
import com.smartseason.media.platform.TenantContext;
import com.smartseason.media.repo.MyMediaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyMediaServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AMINA = UUID.randomUUID();
    private static final UUID JOSEPH = UUID.randomUUID();

    private MyMediaRepository repository;
    private MyMediaService service;
    private MediaAsset asset;

    @BeforeEach
    void setUp() {
        repository = mock(MyMediaRepository.class);
        service = new MyMediaService(repository);
        TenantContext.set(TENANT);

        asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setTenantId(TENANT);
        asset.setOwnerUserId(AMINA);

        when(repository.findAllByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(TENANT, AMINA))
                .thenReturn(List.of(asset));
        when(repository.findAllByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(TENANT, JOSEPH))
                .thenReturn(List.of());
        when(repository.findByIdAndTenantIdAndOwnerUserId(asset.getId(), TENANT, AMINA))
                .thenReturn(Optional.of(asset));
        when(repository.findByIdAndTenantIdAndOwnerUserId(asset.getId(), TENANT, JOSEPH))
                .thenReturn(Optional.empty());
    }

    @Test
    void aWorkerSeesOnlyTheirOwnUploads() {
        assertThat(service.mine(AMINA)).containsExactly(asset);
        assertThat(service.mine(JOSEPH)).isEmpty();
    }

    @Test
    void aWorkerGetsNotFoundOnSomeoneElsesUpload() {
        assertThat(service.one(asset.getId(), AMINA)).isEqualTo(asset);
        assertThatThrownBy(() -> service.one(asset.getId(), JOSEPH))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
