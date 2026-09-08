package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.channel.DeliveryChannel;
import com.kenyarealestate.notification.channel.DeliveryException;
import com.kenyarealestate.notification.client.UserServiceClient;
import com.kenyarealestate.notification.dto.UserContactResponse;
import com.kenyarealestate.notification.entity.*;
import com.kenyarealestate.notification.exception.NotFoundException;
import com.kenyarealestate.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDispatcherTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private NotificationStore store;
    private TemplateRenderer renderer;
    private NotificationPreferenceRepository preferences;
    private UserServiceClient userClient;
    private DeliveryChannel emailChannel;

    @BeforeEach
    void setUp() {
        store = mock(NotificationStore.class);
        renderer = mock(TemplateRenderer.class);
        preferences = mock(NotificationPreferenceRepository.class);
        userClient = mock(UserServiceClient.class);
        emailChannel = mock(DeliveryChannel.class);
        when(emailChannel.type()).thenReturn(Channel.EMAIL);

        UserContactResponse contact = new UserContactResponse(
                USER, "Wanjiru Kamau", "wanjiru@example.co.ke", "+254700000000", "SELLER");
        when(userClient.getContact(USER)).thenReturn(contact);

        when(renderer.findTemplate(anyString(), eq(Channel.IN_APP)))
                .thenThrow(new NotFoundException("no in-app template"));
        // Most template codes have no SMS variant, and the renderer says so by throwing.
        // Stubbing it that way here is not convenience — it is what production does, and
        // it is the mechanism that stops every notification reaching a billable rail.
        when(renderer.findTemplate(anyString(), eq(Channel.SMS)))
                .thenThrow(new NotFoundException("no sms template"));
        when(renderer.findTemplate(anyString(), eq(Channel.EMAIL)))
                .thenReturn(NotificationTemplate.builder()
                        .code("PAYMENT_COMPLETED").channel(Channel.EMAIL)
                        .category(Category.PAYMENT).bodyTemplate("body").build());
        when(renderer.render(any(), any()))
                .thenReturn(new TemplateRenderer.Rendered("Payment received", "body", null));
        when(store.createIfAbsent(any()))
                .thenAnswer(inv -> Optional.of(inv.getArgument(0, Notification.class)));
    }

    private NotificationDispatcher dispatcher(SendRateLimiter limiter) {
        return new NotificationDispatcher(List.of(emailChannel), store, renderer,
                preferences, userClient, limiter, 5);
    }

    private DispatchCommand paymentCommand() {
        return DispatchCommand.builder()
                .userId(USER).templateCode("PAYMENT_COMPLETED").category(Category.PAYMENT)
                .sourceEventType("PAYMENT_COMPLETED").sourceEventId("pay-1")
                .model(Map.of("amount", "25000")).build();
    }

    @Test
    void resolvesTheRecipientAndDelivers() throws Exception {
        dispatcher(new SendRateLimiter(10)).dispatch(paymentCommand());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(emailChannel).deliver(captor.capture());
        assertEquals("wanjiru@example.co.ke", captor.getValue().getRecipientEmail());
        verify(store).markSent(any());
    }

    @Test
    void aDuplicateEventIsNotDeliveredTwice() throws Exception {
        doReturn(Optional.empty()).when(store).createIfAbsent(any());

        dispatcher(new SendRateLimiter(10)).dispatch(paymentCommand());

        verify(emailChannel, never()).deliver(any());
        verify(store, never()).markSent(any());
    }

    @Test
    void anOptedOutUserIsRecordedAsSuppressedRatherThanSent() throws Exception {
        when(preferences.findByUserIdAndCategory(USER, Category.PAYMENT))
                .thenReturn(Optional.of(NotificationPreference.builder()
                        .userId(USER).category(Category.PAYMENT).emailEnabled(false).build()));

        dispatcher(new SendRateLimiter(10)).dispatch(paymentCommand());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(store).createIfAbsent(captor.capture());
        assertEquals(NotificationStatus.SUPPRESSED, captor.getValue().getStatus());
        verify(emailChannel, never()).deliver(any());
    }

    @Test
    void accountNotificationsIgnoreOptOut() throws Exception {
        when(preferences.findByUserIdAndCategory(any(), any()))
                .thenReturn(Optional.of(NotificationPreference.builder()
                        .userId(USER).category(Category.ACCOUNT).emailEnabled(false).build()));

        dispatcher(new SendRateLimiter(10)).dispatch(DispatchCommand.builder()
                .userId(USER).templateCode("PASSWORD_RESET").category(Category.ACCOUNT)
                .sourceEventType("INTERNAL_SEND").sourceEventId("reset-1").build());

        verify(emailChannel).deliver(any());
    }

    @Test
    void aBurstBeyondTheSendRateIsDeferredNotFailed() throws Exception {
        dispatcher(new SendRateLimiter(0)).dispatch(paymentCommand());

        verify(store).deferBriefly(any());
        verify(emailChannel, never()).deliver(any());
        verify(store, never()).markFailed(any(), anyString(), anyInt());
    }

    @Test
    void aTransportFailureIsRecordedForRetry() throws Exception {
        doThrow(new DeliveryException("SMTP send failed: connection refused"))
                .when(emailChannel).deliver(any());

        dispatcher(new SendRateLimiter(10)).dispatch(paymentCommand());

        verify(store).markFailed(any(), eq("SMTP send failed: connection refused"), eq(5));
        verify(store, never()).markSent(any());
    }

    @Test
    void anUnresolvableRecipientStillPersistsTheRowForALaterRetry() throws Exception {
        when(userClient.getContact(USER)).thenReturn(null);
        doThrow(new DeliveryException("No recipient email resolved"))
                .when(emailChannel).deliver(any());

        dispatcher(new SendRateLimiter(10)).dispatch(paymentCommand());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(store).createIfAbsent(captor.capture());
        assertNull(captor.getValue().getRecipientEmail());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> model =
                ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(renderer).render(any(), model.capture());
        assertEquals("there", model.getValue().get("fullName"));

        verify(store).markFailed(any(), anyString(), anyInt());
    }

    @Test
    void aTemplateWithNoSmsVariantSendsNoSms() throws Exception {
        // The dispatcher fans out to SMS, but only templates written for it are sent.
        // Without this the addition of the channel would silently start texting people
        // about everything.
        DeliveryChannel smsChannel = mock(DeliveryChannel.class);
        when(smsChannel.type()).thenReturn(Channel.SMS);

        new NotificationDispatcher(List.of(emailChannel, smsChannel), store, renderer,
                preferences, userClient, new SendRateLimiter(10), 5)
                .dispatch(paymentCommand());

        // Not verifyNoInteractions: the dispatcher calls type() on every channel at
        // construction to build its registry. What matters is that nothing was delivered.
        verify(smsChannel, never()).deliver(any());
        verify(emailChannel).deliver(any());
    }

    @Test
    void aTemplateWithAnSmsVariantIsTextedToTheRecipientsPhone() throws Exception {
        DeliveryChannel smsChannel = mock(DeliveryChannel.class);
        when(smsChannel.type()).thenReturn(Channel.SMS);
        reset(renderer);
        when(renderer.findTemplate(anyString(), eq(Channel.IN_APP)))
                .thenThrow(new NotFoundException("no in-app template"));
        when(renderer.findTemplate(anyString(), eq(Channel.EMAIL)))
                .thenThrow(new NotFoundException("no email template"));
        when(renderer.findTemplate(anyString(), eq(Channel.SMS)))
                .thenReturn(NotificationTemplate.builder()
                        .code("SALE_PAYMENT_RECEIVED").channel(Channel.SMS)
                        .category(Category.PAYMENT).bodyTemplate("body").build());
        when(renderer.render(any(), any()))
                .thenReturn(new TemplateRenderer.Rendered(null, "SmartRE: a buyer has paid.", null));

        new NotificationDispatcher(List.of(emailChannel, smsChannel), store, renderer,
                preferences, userClient, new SendRateLimiter(10), 5)
                .dispatch(paymentCommand());

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(smsChannel).deliver(sent.capture());
        assertEquals("+254700000000", sent.getValue().getRecipientPhone(),
                "the phone comes from the resolved contact, not the command");
        assertNull(sent.getValue().getHtmlBody(), "a phone has no use for HTML");
    }
}
