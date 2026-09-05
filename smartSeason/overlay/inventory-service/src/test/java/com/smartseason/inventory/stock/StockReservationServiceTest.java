package com.smartseason.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StockReservationServiceTest {

    private final StockItemRepository stockItems = mock(StockItemRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);

    private final StockReservationService service =
            new StockReservationService(stockItems, reservations, events);

    private final UUID tenant = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private StockItem item;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenant);

        item = new StockItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(tenant);
        item.setCommodityCode("MAIZE");
        item.setQuantity(new BigDecimal("1000"));
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setUnit("kg");

        when(stockItems.findByIdAndTenantId(item.getId(), tenant)).thenReturn(Optional.of(item));
        when(stockItems.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservations.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation value = invocation.getArgument(0);
            if (value.getId() == null) {
                value.setId(UUID.randomUUID());
            }
            return value;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReservationRequest request(String quantity) {
        return new ReservationRequest(item.getId(), orderId, new BigDecimal(quantity), null);
    }

    @Test
    @DisplayName("reserving holds the quantity without reducing the stock on hand")
    void reserveHoldsWithoutDeducting() {
        Reservation reservation = service.reserve(request("300"));

        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.HELD);
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("300");
        assertThat(item.getQuantity())
                .as("the goods are still physically present until the reservation is consumed")
                .isEqualByComparingTo("1000");
        assertThat(StockReservationService.availableOf(item)).isEqualByComparingTo("700");
        verify(events).publish(eq("market"), eq("StockReserved"), any(), eq(orderId));
    }

    @Test
    @DisplayName("reserving sets an expiry so abandoned carts do not hold stock forever")
    void reservationExpires() {
        Reservation reservation = service.reserve(request("100"));

        assertThat(reservation.getExpiresAt()).isAfter(Instant.now());
    }

    @Nested
    class Overselling {

        @Test
        @DisplayName("a request beyond what is available is refused")
        void cannotReserveMoreThanAvailable() {
            assertThatThrownBy(() -> service.reserve(request("1200")))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("only 1000");

            verify(reservations, never()).save(any());
        }

        @Test
        @DisplayName("existing holds count against availability, so two orders cannot take the same goods")
        void existingHoldsReduceAvailability() {
            item.setReservedQuantity(new BigDecimal("800"));

            assertThatThrownBy(() -> service.reserve(request("300")))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("only 200");
        }

        @Test
        @DisplayName("reserving exactly what is left is allowed")
        void exactAvailabilityIsAllowed() {
            assertThat(service.reserve(request("1000")).getStatus())
                    .isEqualTo(Reservation.Status.HELD);
            assertThat(StockReservationService.availableOf(item)).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Transitions {

        private Reservation held() {
            Reservation reservation = new Reservation();
            reservation.setId(UUID.randomUUID());
            reservation.setTenantId(tenant);
            reservation.setStockItemId(item.getId());
            reservation.setOrderId(orderId);
            reservation.setQuantity(new BigDecimal("300"));
            reservation.setStatus(Reservation.Status.HELD);
            item.setReservedQuantity(new BigDecimal("300"));
            when(reservations.findByIdAndTenantId(reservation.getId(), tenant))
                    .thenReturn(Optional.of(reservation));
            return reservation;
        }

        @Test
        @DisplayName("releasing returns the hold and leaves the stock on hand untouched")
        void releaseReturnsHold() {
            Reservation reservation = held();

            service.release(reservation.getId());

            assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.RELEASED);
            assertThat(item.getReservedQuantity()).isEqualByComparingTo("0");
            assertThat(item.getQuantity()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("consuming removes the goods from stock as well as clearing the hold")
        void consumeDeductsStock() {
            Reservation reservation = held();

            service.consume(reservation.getId());

            assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.CONSUMED);
            assertThat(item.getQuantity()).isEqualByComparingTo("700");
            assertThat(item.getReservedQuantity()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a reservation cannot be released twice")
        void doubleReleaseRefused() {
            Reservation reservation = held();
            service.release(reservation.getId());

            assertThatThrownBy(() -> service.release(reservation.getId()))
                    .isInstanceOf(DomainRuleException.class)
                    .hasMessageContaining("can no longer be");
        }

        @Test
        @DisplayName("an unknown reservation is a not-found, not a silent no-op")
        void unknownReservationThrows() {
            UUID missing = UUID.randomUUID();
            when(reservations.findByIdAndTenantId(missing, tenant)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.release(missing))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("expired holds are swept and their stock returned to availability")
    void expiredHoldsAreSwept() {
        Reservation stale = new Reservation();
        stale.setId(UUID.randomUUID());
        stale.setTenantId(tenant);
        stale.setStockItemId(item.getId());
        stale.setOrderId(orderId);
        stale.setQuantity(new BigDecimal("250"));
        stale.setStatus(Reservation.Status.HELD);
        item.setReservedQuantity(new BigDecimal("250"));

        when(reservations.findAllByStatusAndTenantIdAndExpiresAtBefore(
                eq(Reservation.Status.HELD), eq(tenant), any())).thenReturn(List.of(stale));

        assertThat(service.expireOverdue()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(Reservation.Status.EXPIRED);
        assertThat(item.getReservedQuantity()).isEqualByComparingTo("0");
    }
}
