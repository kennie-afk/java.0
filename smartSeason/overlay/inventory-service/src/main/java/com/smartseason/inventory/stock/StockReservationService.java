package com.smartseason.inventory.stock;

import com.smartseason.inventory.domain.Reservation;
import com.smartseason.inventory.domain.StockItem;
import com.smartseason.inventory.platform.DomainRuleException;
import com.smartseason.inventory.platform.EventPublisher;
import com.smartseason.inventory.platform.ResourceNotFoundException;
import com.smartseason.inventory.platform.TenantContext;
import com.smartseason.inventory.repo.ReservationRepository;
import com.smartseason.inventory.repo.StockItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);
    private static final int DEFAULT_HOLD_MINUTES = 30;

    private final StockItemRepository stockItems;
    private final ReservationRepository reservations;
    private final EventPublisher events;

    public StockReservationService(StockItemRepository stockItems,
                                   ReservationRepository reservations,
                                   EventPublisher events) {
        this.stockItems = stockItems;
        this.reservations = reservations;
        this.events = events;
    }

    public static BigDecimal availableOf(StockItem item) {
        BigDecimal total = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
        BigDecimal held = item.getReservedQuantity() == null
                ? BigDecimal.ZERO
                : item.getReservedQuantity();
        return total.subtract(held);
    }

    @Transactional
    public Reservation reserve(ReservationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        StockItem item = stockItems.findByIdAndTenantId(request.stockItemId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("StockItem", request.stockItemId()));

        BigDecimal available = availableOf(item);
        if (available.compareTo(request.quantity()) < 0) {
            throw new InsufficientStockException(request.quantity(), available);
        }

        item.setReservedQuantity(
                (item.getReservedQuantity() == null ? BigDecimal.ZERO : item.getReservedQuantity())
                        .add(request.quantity()));
        stockItems.save(item);

        Reservation reservation = new Reservation();
        reservation.setTenantId(tenantId);
        reservation.setStockItemId(item.getId());
        reservation.setOrderId(request.orderId());
        reservation.setQuantity(request.quantity());
        reservation.setReservedAt(Instant.now());
        reservation.setExpiresAt(Instant.now().plus(
                request.holdMinutes() == null ? DEFAULT_HOLD_MINUTES : request.holdMinutes(),
                ChronoUnit.MINUTES));
        reservation.setStatus(Reservation.Status.HELD);
        Reservation saved = reservations.save(reservation);

        events.publish("market", "StockReserved", saved.getId(), saved.getOrderId());
        log.info("Reserved {} of stock item {} for order {}",
                request.quantity(), item.getId(), request.orderId());
        return saved;
    }

    @Transactional
    public void release(UUID reservationId) {
        transition(reservationId, Reservation.Status.RELEASED, true, "StockReleased");
    }

    @Transactional
    public void consume(UUID reservationId) {
        transition(reservationId, Reservation.Status.CONSUMED, false, "StockConsumed");
    }

    @Transactional
    public int expireOverdue() {
        UUID tenantId = TenantContext.requireTenantId();
        List<Reservation> overdue = reservations.findAllByStatusAndTenantIdAndExpiresAtBefore(
                Reservation.Status.HELD, tenantId, Instant.now());

        for (Reservation reservation : overdue) {
            applyRelease(reservation, tenantId);
            reservation.setStatus(Reservation.Status.EXPIRED);
            reservation.setReleasedAt(Instant.now());
            reservations.save(reservation);
            events.publish("market", "StockReservationExpired",
                    reservation.getId(), reservation.getOrderId());
        }
        return overdue.size();
    }

    private void transition(UUID reservationId, Reservation.Status target,
                            boolean returnsStock, String eventType) {
        UUID tenantId = TenantContext.requireTenantId();

        Reservation reservation = reservations.findByIdAndTenantId(reservationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));

        if (reservation.getStatus() != Reservation.Status.HELD) {
            throw new DomainRuleException(
                    "Reservation %s is %s and can no longer be %s"
                            .formatted(reservationId, reservation.getStatus(), target));
        }

        if (returnsStock) {
            applyRelease(reservation, tenantId);
        } else {
            StockItem item = stockItems.findByIdAndTenantId(reservation.getStockItemId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "StockItem", reservation.getStockItemId()));
            item.setQuantity(item.getQuantity().subtract(reservation.getQuantity()));
            item.setReservedQuantity(item.getReservedQuantity().subtract(reservation.getQuantity()));
            stockItems.save(item);
        }

        reservation.setStatus(target);
        reservation.setReleasedAt(Instant.now());
        reservations.save(reservation);
        events.publish("market", eventType, reservation.getId(), reservation.getOrderId());
    }

    private void applyRelease(Reservation reservation, UUID tenantId) {
        stockItems.findByIdAndTenantId(reservation.getStockItemId(), tenantId).ifPresent(item -> {
            BigDecimal held = item.getReservedQuantity() == null
                    ? BigDecimal.ZERO
                    : item.getReservedQuantity();
            item.setReservedQuantity(held.subtract(reservation.getQuantity()).max(BigDecimal.ZERO));
            stockItems.save(item);
        });
    }
}
