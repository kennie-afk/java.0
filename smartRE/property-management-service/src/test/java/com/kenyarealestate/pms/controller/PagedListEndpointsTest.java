package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.dto.UnitResponse;
import com.kenyarealestate.pms.service.LeaseService;
import com.kenyarealestate.pms.service.RentInvoiceService;
import com.kenyarealestate.pms.service.RentPaymentService;
import com.kenyarealestate.pms.service.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three lists that used to be unbounded (units on a property, a unit's lease history,
 * the payments on an invoice) are now bounded pages that still arrive as plain arrays.
 */
class PagedListEndpointsTest {

    private static final UUID LANDLORD = UUID.randomUUID();
    private static final UUID PROPERTY = UUID.randomUUID();

    private final CallerIdentity caller = mock(CallerIdentity.class);
    private final HttpServletRequest request = new MockHttpServletRequest();

    // ---- controllers ------------------------------------------------------------------------

    @Test
    void unitsOnAPropertyDefaultToTheCapAndReportTheTotalInHeaders() {
        UnitService units = mock(UnitService.class);
        when(caller.userId(request)).thenReturn(LANDLORD);
        UnitResponse one = UnitResponse.builder().id(UUID.randomUUID()).label("A1").build();
        when(units.listByProperty(eq(LANDLORD), eq(PROPERTY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(one), PageRequest.of(0, 200), 450));

        ResponseEntity<List<UnitResponse>> response =
                new UnitController(units, mock(LeaseService.class), caller).byProperty(PROPERTY, 0, 200, request);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(units).listByProperty(eq(LANDLORD), eq(PROPERTY), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(one);
        assertThat(response.getHeaders().getFirst("X-Total-Count")).isEqualTo("450");
        assertThat(response.getHeaders().getFirst("X-Has-More")).isEqualTo("true");
    }

    @Test
    void theLastPageSaysThereIsNoMore() {
        UnitService units = mock(UnitService.class);
        when(caller.userId(request)).thenReturn(LANDLORD);
        when(units.listByProperty(eq(LANDLORD), eq(PROPERTY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 200), 450));

        ResponseEntity<List<UnitResponse>> response =
                new UnitController(units, mock(LeaseService.class), caller).byProperty(PROPERTY, 2, 200, request);

        assertThat(response.getHeaders().getFirst("X-Has-More")).isEqualTo("false");
    }

    @Test
    void sizeAndPageAreBoundedByValidationOnEveryNewlyPagedList() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        var executable = Validation.buildDefaultValidatorFactory().getValidator()
                .forExecutables();
        UnitController units = new UnitController(mock(UnitService.class), mock(LeaseService.class), caller);
        InvoiceController invoices = new InvoiceController(
                mock(RentInvoiceService.class), mock(RentPaymentService.class), caller);

        Method byProperty = UnitController.class.getMethod(
                "byProperty", UUID.class, int.class, int.class, HttpServletRequest.class);
        Method history = UnitController.class.getMethod(
                "history", UUID.class, int.class, int.class, HttpServletRequest.class);
        Method payments = InvoiceController.class.getMethod(
                "paymentsFor", UUID.class, int.class, int.class, HttpServletRequest.class);

        for (Object[] bad : new Object[][] {
                {UUID.randomUUID(), 0, 201, request}, {UUID.randomUUID(), 0, 0, request},
                {UUID.randomUUID(), -1, 50, request}}) {
            assertThat(executable.validateParameters(units, byProperty, bad)).as("byProperty %s", bad[1] + "/" + bad[2]).isNotEmpty();
            assertThat(executable.validateParameters(units, history, bad)).as("history").isNotEmpty();
            assertThat(executable.validateParameters(invoices, payments, bad)).as("payments").isNotEmpty();
        }
        Object[] fine = {UUID.randomUUID(), 3, 200, request};
        assertThat(executable.validateParameters(units, byProperty, fine)).isEmpty();
        assertThat(validator).isNotNull();
    }
}
