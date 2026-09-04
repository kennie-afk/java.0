package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Channel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DedupKeysTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void sameEventForSameUserAndChannelProducesTheSameKey() {
        String a = DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.EMAIL);
        String b = DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.EMAIL);
        assertEquals(a, b, "a redelivered Kafka event must collide on the unique dedup_key");
    }

    @Test
    void channelIsPartOfTheKey() {
        assertNotEquals(
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.EMAIL),
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.IN_APP));
    }

    @Test
    void recipientIsPartOfTheKey() {
        assertNotEquals(
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.EMAIL),
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", OTHER, Channel.EMAIL));
    }

    @Test
    void differentEventsOfTheSameTypeDoNotCollide() {
        assertNotEquals(
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-1", USER, Channel.EMAIL),
                DedupKeys.forEvent("PAYMENT_COMPLETED", "pay-2", USER, Channel.EMAIL));
    }

    @Test
    void keyFitsTheColumn() {
        assertEquals(64, DedupKeys.forEvent("X", "1", USER, Channel.EMAIL).length());
    }
}
