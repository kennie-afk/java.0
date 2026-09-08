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
    void paymentReceiptGoesToThePayerAndTheSellerIsToldSeparately() throws Exception {
        // This test used to assert a single dispatch, pinning the behaviour where only
        // the buyer heard about a payment. That was the bug, not the contract: the
        // receipt is still the buyer's, but the seller now gets their own message under
        // a different template rather than a copy of the buyer's receipt.
        router.route("""
            {"eventType":"PAYMENT_COMPLETED",
             "paymentId":"44444444-4444-4444-4444-444444444444",
             "buyerId":"11111111-1111-1111-1111-111111111111",
             "sellerId":"22222222-2222-2222-2222-222222222222",
             "amount":25000,"currency":"KES","mpesaReceiptNumber":"QCB7Y2XK91",
             "paymentType":"DEPOSIT"}
            """, "payment-events");

        var sent = captureAll(2);

        DispatchCommand receipt = sent.get(0);
        assertEquals("PAYMENT_COMPLETED", receipt.getTemplateCode());
        assertEquals("11111111-1111-1111-1111-111111111111", receipt.getUserId().toString(),
                "the receipt is addressed to whoever paid");
        assertEquals("QCB7Y2XK91", receipt.getModel().get("mpesaReceiptNumber"));
        assertEquals("KES", receipt.getModel().get("currency"));
        assertTrue(String.valueOf(receipt.getModel().get("receiptLink")).endsWith(
                "/payments/44444444-4444-4444-4444-444444444444"));

        DispatchCommand toSeller = sent.get(1);
        assertEquals("22222222-2222-2222-2222-222222222222", toSeller.getUserId().toString());
        assertNotEquals("PAYMENT_COMPLETED", toSeller.getTemplateCode(),
                "the seller gets their own wording, not the buyer's receipt");
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

    // --- Both sides of a payment -------------------------------------------------

    private java.util.List<DispatchCommand> captureAll(int times) {
        ArgumentCaptor<DispatchCommand> captor = ArgumentCaptor.forClass(DispatchCommand.class);
        verify(dispatcher, times(times)).dispatch(captor.capture());
        return captor.getAllValues();
    }

    private String paymentEvent(String type) {
        return """
            {"eventType":"PAYMENT_COMPLETED",
             "paymentId":"44444444-4444-4444-4444-444444444444",
             "buyerId":"11111111-1111-1111-1111-111111111111",
             "sellerId":"22222222-2222-2222-2222-222222222222",
             "amount":"4500000","currency":"KES",
             "mpesaReceiptNumber":"QCB7Y2XK91",
             "paymentType":"%s"}
            """.formatted(type);
    }

    @Test
    void aSaleTellsBothTheBuyerAndTheSeller() throws Exception {
        router.route(paymentEvent("FULL_PAYMENT"), "payment-events");

        var sent = captureAll(2);
        assertEquals("PAYMENT_COMPLETED", sent.get(0).getTemplateCode());
        assertEquals("11111111-1111-1111-1111-111111111111", sent.get(0).getUserId().toString());
        assertEquals("SALE_PAYMENT_RECEIVED", sent.get(1).getTemplateCode());
        assertEquals("22222222-2222-2222-2222-222222222222", sent.get(1).getUserId().toString());
    }

    @Test
    void aViewingFeeDoesNotTellTheSellerTheirPropertySold() throws Exception {
        router.route(paymentEvent("VIEWING_FEE"), "payment-events");

        var sent = captureAll(2);
        // Money received, yes. A sale, no — and the wording has to know the difference.
        assertEquals("SELLER_PAYMENT_RECEIVED", sent.get(1).getTemplateCode());
    }

    @Test
    void rentIsNotAnnouncedTwice() throws Exception {
        // RENT_RECEIVED already tells the landlord, with the right link and wording.
        router.route(paymentEvent("RENT"), "payment-events");

        var sent = captureAll(1);
        assertEquals("PAYMENT_COMPLETED", sent.get(0).getTemplateCode());
    }

    @Test
    void aPaymentWithNoSellerStillReachesTheBuyer() throws Exception {
        router.route("""
            {"eventType":"PAYMENT_COMPLETED",
             "paymentId":"44444444-4444-4444-4444-444444444444",
             "buyerId":"11111111-1111-1111-1111-111111111111",
             "amount":"1000","paymentType":"PROFILE_ACCESS"}
            """, "payment-events");

        var sent = captureAll(1);
        assertEquals("PAYMENT_COMPLETED", sent.get(0).getTemplateCode());
    }

    // --- Tenancy and viewings ----------------------------------------------------

    @Test
    void anActivatedLeaseReachesTheTenantsAccount() throws Exception {
        router.route("""
            {"eventType":"LEASE_ACTIVATED",
             "leaseId":"55555555-5555-5555-5555-555555555555",
             "tenantId":"66666666-6666-6666-6666-666666666666",
             "tenantUserId":"77777777-7777-7777-7777-777777777777",
             "rentAmount":"85000","billingDay":1,"startDate":"2026-01-01"}
            """, "pms-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("LEASE_ACTIVATED", cmd.getTemplateCode());
        assertEquals(Category.TENANCY, cmd.getCategory());
        // The tenant's account, never the tenant record id.
        assertEquals("77777777-7777-7777-7777-777777777777", cmd.getUserId().toString());
    }

    @Test
    void anUnlinkedTenantIsSkippedRatherThanGuessedAt() throws Exception {
        // A tenant on the landlord's books with no SmartRE account. There is nobody to
        // write to, and tenantId is not a user id.
        router.route("""
            {"eventType":"LEASE_ACTIVATED",
             "leaseId":"55555555-5555-5555-5555-555555555555",
             "tenantId":"66666666-6666-6666-6666-666666666666",
             "rentAmount":"85000","billingDay":1}
            """, "pms-events");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void anEndedLeaseReachesTheTenant() throws Exception {
        router.route("""
            {"eventType":"LEASE_ENDED",
             "leaseId":"55555555-5555-5555-5555-555555555555",
             "tenantUserId":"77777777-7777-7777-7777-777777777777",
             "reason":"TERMINATED"}
            """, "pms-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("LEASE_ENDED", cmd.getTemplateCode());
    }

    @Test
    void aCompletedViewingInvitesTheBuyerToReview() throws Exception {
        router.route("""
            {"eventType":"VIEWING_COMPLETED",
             "viewingId":"88888888-8888-8888-8888-888888888888",
             "buyerId":"11111111-1111-1111-1111-111111111111",
             "propertyId":"99999999-9999-9999-9999-999999999999"}
            """, "viewing-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("VIEWING_COMPLETED", cmd.getTemplateCode());
        assertEquals(Category.VIEWING, cmd.getCategory());
        assertTrue(cmd.getModel().get("reviewLink").toString()
                .contains("99999999-9999-9999-9999-999999999999"));
    }

    // --- Money as a person reads it ----------------------------------------------

    @Test
    void moneyIsGroupedAndNotLeftAsARawDecimal() throws Exception {
        // A real email read "KES 25000.0" before this: the value arrives as whatever the
        // producing service's BigDecimal serialised to.
        router.route("""
            {"eventType":"RENT_RECEIVED",
             "invoiceId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
             "tenantUserId":"77777777-7777-7777-7777-777777777777",
             "unitLabel":"B1","invoiceNumber":"RNT-1",
             "amount":15000,"balance":25000.0}
            """, "pms-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("15,000", cmd.getModel().get("amount"));
        assertEquals("25,000", cmd.getModel().get("balance"));
    }

    @Test
    void aFractionalAmountKeepsBothDecimalPlaces() throws Exception {
        router.route("""
            {"eventType":"RENT_RECEIVED",
             "invoiceId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
             "tenantUserId":"77777777-7777-7777-7777-777777777777",
             "unitLabel":"B1","invoiceNumber":"RNT-1",
             "amount":1234.5,"balance":0}
            """, "pms-events");

        DispatchCommand cmd = captureDispatch();
        assertEquals("1,234.50", cmd.getModel().get("amount"));
        assertEquals("0", cmd.getModel().get("balance"));
    }
}
