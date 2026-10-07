package com.kenyarealestate.payment.controller;

import com.kenyarealestate.payment.entity.MpesaRawCallback;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.security.CallbackIpPolicy;
import com.kenyarealestate.payment.security.JwtUtil;
import com.kenyarealestate.payment.service.PaymentAuditService;
import com.kenyarealestate.payment.service.ReceiptService;
import com.kenyarealestate.payment.service.RevenueService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Raw M-Pesa callbacks are append-only and never pruned: both ways of reading them are bounded and pageable. */
class RawCallbacksPagingTest {

    private final MpesaRawCallbackRepository repo = mock(MpesaRawCallbackRepository.class);
    private final RevenueController controller = new RevenueController(
            mock(RevenueService.class), mock(JwtUtil.class), mock(PaymentAuditService.class),
            mock(ReceiptService.class), repo, mock(CallbackIpPolicy.class));

    @Test
    void theOnePaymentFormIsCappedAndPagedInsteadOfUnbounded() {
        UUID payment = UUID.randomUUID();
        when(repo.findByPaymentId(eq(payment), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new MpesaRawCallback()), PageRequest.of(0, 500), 1200));

        ResponseEntity<List<MpesaRawCallback>> response = controller.rawCallbacks(payment, 100_000, 0);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findByPaymentId(eq(payment), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
        assertThat(page.getValue().getSort().toString()).isEqualTo("receivedAt: ASC,id: ASC");
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("1200");
        assertThat(response.getHeaders().getFirst("X-Has-More")).isEqualTo("true");
        verify(repo, never()).findByPaymentIdOrderByReceivedAtAsc(any());
    }

    @Test
    void theMostRecentFormCanReachPastTheFirstPage() {
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 100), 250));

        ResponseEntity<List<MpesaRawCallback>> response = controller.rawCallbacks(null, 100, 2);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getSort().toString()).isEqualTo("receivedAt: DESC,id: ASC");
        assertThat(response.getHeaders().getFirst("X-Has-More")).isEqualTo("false");
    }

    @Test
    void aZeroOrNegativeLimitStillReturnsAtLeastOneRow() {
        when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.rawCallbacks(null, -3, -1);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).findAll(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(1);
        assertThat(page.getValue().getPageNumber()).isZero();
    }
}
