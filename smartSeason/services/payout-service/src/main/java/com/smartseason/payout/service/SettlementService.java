package com.smartseason.payout.service;

import com.smartseason.payout.domain.Settlement;
import com.smartseason.payout.platform.EventPublisher;
import com.smartseason.payout.platform.PageResponse;
import com.smartseason.payout.platform.ResourceNotFoundException;
import com.smartseason.payout.platform.TenantContext;
import com.smartseason.payout.repo.SettlementRepository;
import com.smartseason.payout.web.dto.SettlementCreateRequest;
import com.smartseason.payout.web.dto.SettlementResponse;
import com.smartseason.payout.web.dto.SettlementUpdateRequest;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private static final String RESOURCE = "Settlement";

    private final SettlementRepository repository;
    private final EventPublisher events;

    public SettlementService(SettlementRepository repository, EventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public PageResponse<SettlementResponse> list(Pageable pageable) {
        return PageResponse.from(
                repository.findAllByTenantId(TenantContext.requireTenantId(), pageable)
                        .map(SettlementResponse::from));
    }

    public SettlementResponse get(UUID id) {
        return SettlementResponse.from(require(id));
    }

    public long count() {
        return repository.countByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public SettlementResponse create(SettlementCreateRequest request) {
        Settlement entity = new Settlement();
        entity.setTenantId(TenantContext.requireTenantId());
        entity.setSettlementNumber(request.settlementNumber());
        entity.setPayeeOrgId(request.payeeOrgId());
        entity.setPayeeUserId(request.payeeUserId());
        entity.setOrderId(request.orderId());
        entity.setGrossAmount(request.grossAmount());
        entity.setCommission(request.commission());
        entity.setFees(request.fees());
        entity.setNetAmount(request.netAmount());
        entity.setCurrency(request.currency());
        entity.setPeriodStart(request.periodStart());
        entity.setPeriodEnd(request.periodEnd());
        entity.setDueAt(request.dueAt());
        entity.setStatus(request.status());
        entity.setApprovedBy(request.approvedBy());

        Settlement saved = repository.save(entity);
        events.publish("money", "SettlementCreated", saved.getId(), SettlementResponse.from(saved));
        return SettlementResponse.from(saved);
    }

    @Transactional
    public SettlementResponse update(UUID id, SettlementUpdateRequest request) {
        Settlement entity = require(id);
        if (request.settlementNumber() != null) {
            entity.setSettlementNumber(request.settlementNumber());
        }
        if (request.payeeOrgId() != null) {
            entity.setPayeeOrgId(request.payeeOrgId());
        }
        if (request.payeeUserId() != null) {
            entity.setPayeeUserId(request.payeeUserId());
        }
        if (request.orderId() != null) {
            entity.setOrderId(request.orderId());
        }
        if (request.grossAmount() != null) {
            entity.setGrossAmount(request.grossAmount());
        }
        if (request.commission() != null) {
            entity.setCommission(request.commission());
        }
        if (request.fees() != null) {
            entity.setFees(request.fees());
        }
        if (request.netAmount() != null) {
            entity.setNetAmount(request.netAmount());
        }
        if (request.currency() != null) {
            entity.setCurrency(request.currency());
        }
        if (request.periodStart() != null) {
            entity.setPeriodStart(request.periodStart());
        }
        if (request.periodEnd() != null) {
            entity.setPeriodEnd(request.periodEnd());
        }
        if (request.dueAt() != null) {
            entity.setDueAt(request.dueAt());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.approvedBy() != null) {
            entity.setApprovedBy(request.approvedBy());
        }

        Settlement saved = repository.save(entity);
        events.publish("money", "SettlementUpdated", saved.getId(), SettlementResponse.from(saved));
        return SettlementResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Settlement entity = require(id);
        repository.delete(entity);
        events.publish("money", "SettlementDeleted", id, null);
    }

    private Settlement require(UUID id) {
        return repository.findByIdAndTenantId(id, TenantContext.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
