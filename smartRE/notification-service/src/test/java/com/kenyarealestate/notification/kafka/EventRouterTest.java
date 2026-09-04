package com.kenyarealestate.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.notification.entity.Category;
import com.kenyarealestate.notification.service.DispatchCommand;
import com.kenyarealestate.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventRouterTest {

    private NotificationDispatcher dispatcher;
    private EventRouter router;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        router = new EventRouter(new ObjectMapper(), dispatcher, "http://localhost:3000");
    }

    private DispatchCommand captureDispatch() {
        ArgumentCaptor<DispatchCommand> captor = ArgumentCaptor.forClass(DispatchCommand.class);
        verify(dispatcher).dispatch(captor.capture());
        return captor.getValue();
    }

    @Test
    void routesIdentityApprovedToTheSeller() throws Exception {
        router.route("""
            {"eventType":"IDENTITY_APPROVED",
             "sellerId":"11111111-1111-1111-1111-111111111111",
             "verificationId":"33333333-3333-3333-3333-333333333333",
             "expiresAt":"2027-01-01T00:00:00"}
            """, "verification-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("IDENTITY_APPROVED", cmd.getTemplateCode());
        assertEquals(Category.VERIFICATION, cmd.getCategory());
        assertEquals("11111111-1111-1111-1111-111111111111", cmd.getUserId().toString());
        assertEquals("33333333-3333-3333-3333-333333333333", cmd.getSourceEventId());
        assertEquals("2027-01-01T00:00:00", cmd.getModel().get("expiresAt"));
    }

    @Test
    void paymentReceiptGoesToThePayerNotTheSeller() throws Exception {
        router.route("""
            {"eventType":"PAYMENT_COMPLETED",
             "paymentId":"44444444-4444-4444-4444-444444444444",
             "buyerId":"11111111-1111-1111-1111-111111111111",
             "sellerId":"22222222-2222-2222-2222-222222222222",
             "amount":25000,"currency":"KES","mpesaReceiptNumber":"QCB7Y2XK91",
             "paymentType":"DEPOSIT"}
            """, "payment-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("11111111-1111-1111-1111-111111111111", cmd.getUserId().toString(),
                "the receipt is addressed to whoever paid");
        assertEquals("QCB7Y2XK91", cmd.getModel().get("mpesaReceiptNumber"));
        assertEquals("KES", cmd.getModel().get("currency"));
        assertTrue(String.valueOf(cmd.getModel().get("receiptLink")).endsWith(
                "/payments/44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void missingOptionalFieldsRenderAsADashRatherThanNull() throws Exception {
        router.route("""
            {"eventType":"OWNERSHIP_APPROVED",
             "sellerId":"11111111-1111-1111-1111-111111111111",
             "verificationId":"33333333-3333-3333-3333-333333333333",
             "propertyId":"55555555-5555-5555-5555-555555555555"}
            """, "verification-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("—", cmd.getModel().get("parcelNumber"));
        assertEquals("—", cmd.getModel().get("titleDeedNumber"));
    }

    @Test
    void unmappedEventTypesAreIgnoredRatherThanThrown() throws Exception {
        router.route("""
            {"eventType":"SOME_FUTURE_EVENT","propertyId":"55555555-5555-5555-5555-555555555555"}
            """, "property-events");
        verifyNoInteractions(dispatcher);
    }

    @Test
    void envelopesWithoutAnEventTypeAreIgnored() throws Exception {
        router.route("{\"propertyId\":\"55555555-5555-5555-5555-555555555555\"}", "property-events");
        verifyNoInteractions(dispatcher);
    }

    @Test
    void anUnparseableUserIdIsSkippedRatherThanCrashing() throws Exception {
        router.route("""
            {"eventType":"PAYMENT_COMPLETED","paymentId":"44444444-4444-4444-4444-444444444444",
             "buyerId":"not-a-uuid"}
            """, "payment-events");
        verifyNoInteractions(dispatcher);
    }
}
