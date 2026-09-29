package com.smartseason.agronomy.myscouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartseason.agronomy.domain.ScoutingReport;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.MyScoutingReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyScoutingServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AMINA = UUID.randomUUID();
    private static final UUID JOSEPH = UUID.randomUUID();

    private MyScoutingReportRepository repository;
    private MyScoutingService service;
    private ScoutingReport report;

    @BeforeEach
    void setUp() {
        repository = mock(MyScoutingReportRepository.class);
        service = new MyScoutingService(repository);
        TenantContext.set(TENANT);

        report = new ScoutingReport();
        report.setId(UUID.randomUUID());
        report.setTenantId(TENANT);
        report.setScoutedBy(AMINA);

        when(repository.findAllByTenantIdAndScoutedByOrderByScoutedAtDesc(TENANT, AMINA))
                .thenReturn(List.of(report));
        when(repository.findAllByTenantIdAndScoutedByOrderByScoutedAtDesc(TENANT, JOSEPH))
                .thenReturn(List.of());
        when(repository.findByIdAndTenantIdAndScoutedBy(report.getId(), TENANT, AMINA))
                .thenReturn(Optional.of(report));
        when(repository.findByIdAndTenantIdAndScoutedBy(report.getId(), TENANT, JOSEPH))
                .thenReturn(Optional.empty());
    }

    @Test
    void aWorkerSeesOnlyTheirOwnScoutingReports() {
        assertThat(service.mine(AMINA)).containsExactly(report);
        assertThat(service.mine(JOSEPH)).isEmpty();
    }

    @Test
    void aWorkerGetsNotFoundOnSomeoneElsesScoutingReport() {
        assertThat(service.one(report.getId(), AMINA)).isEqualTo(report);
        assertThatThrownBy(() -> service.one(report.getId(), JOSEPH))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
