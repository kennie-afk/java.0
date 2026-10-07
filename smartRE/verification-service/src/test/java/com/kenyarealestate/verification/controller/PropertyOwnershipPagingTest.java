package com.kenyarealestate.verification.controller;

import com.kenyarealestate.verification.dto.ownership.OwnershipVerificationResponse;
import com.kenyarealestate.verification.enums.OwnershipVerificationStatus;
import com.kenyarealestate.verification.security.JwtUtil;
import com.kenyarealestate.verification.service.BulkIntakeService;
import com.kenyarealestate.verification.service.PropertyOwnershipVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A seller's own filings and the admin queue are bounded, whatever the caller asks for. */
class PropertyOwnershipPagingTest {

    private final PropertyOwnershipVerificationService service = mock(PropertyOwnershipVerificationService.class);
    private final PropertyOwnershipController controller = new PropertyOwnershipController(
            service, mock(BulkIntakeService.class), mock(JwtUtil.class));
    private final UUID user = UUID.randomUUID();
    private MockHttpServletRequest request;

    @BeforeEach
    void signedIn() {
        request = new MockHttpServletRequest();
        request.setAttribute("authenticatedUserId", user);
    }

    private Pageable sentToMine() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getByUserId(eq(user), page.capture());
        return page.getValue();
    }

    @Test
    void myFilingsStayAPlainArrayWithTheTotalInHeaders() {
        OwnershipVerificationResponse one = new OwnershipVerificationResponse();
        when(service.getByUserId(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(one), PageRequest.of(0, 100), 250));

        ResponseEntity<List<OwnershipVerificationResponse>> response = controller.getMyVerifications(request, 0, 100);

        assertThat(response.getBody()).containsExactly(one);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("250");
        assertThat(response.getHeaders().getFirst("X-Has-More")).isEqualTo("true");
        assertThat(sentToMine().getPageSize()).isEqualTo(100);
    }

    @Test
    void anAbsurdSizeOrNegativePageIsClamped() {
        when(service.getByUserId(eq(user), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.getMyVerifications(request, -5, 1_000_000);

        Pageable sent = sentToMine();
        assertThat(sent.getPageSize()).isEqualTo(PropertyOwnershipController.MAX_PAGE_SIZE);
        assertThat(sent.getPageNumber()).isZero();
    }

    @Test
    void theAdminQueueCapsItsSizeAndIgnoresASortColumnItDoesNotKnow() {
        when(service.getQueue(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.adminQueue(OwnershipVerificationStatus.HUMAN_REVIEW, 0, 5_000, "passwordHash;drop", "DESC");

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getQueue(eq(OwnershipVerificationStatus.HUMAN_REVIEW), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(PropertyOwnershipController.MAX_QUEUE_SIZE);
        // An unknown column falls back to createdAt, and id breaks ties so pages never repeat a row.
        assertThat(page.getValue().getSort().toString()).isEqualTo("createdAt: DESC,id: ASC");
    }

    @Test
    void aKnownSortColumnIsKept() {
        when(service.getQueue(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.adminQueue(OwnershipVerificationStatus.HUMAN_REVIEW, 2, 20, "updatedAt", "ASC");

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getQueue(eq(OwnershipVerificationStatus.HUMAN_REVIEW), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getSort().toString()).isEqualTo("updatedAt: ASC,id: ASC");
    }
}
