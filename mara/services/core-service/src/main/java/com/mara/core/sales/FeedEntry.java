package com.mara.core.sales;

/** One verified journal entry as sync-service publishes it on its internal feed. */
public record FeedEntry(String terminalId, String tenantId, long sequence, long epochSecond, int nano, String sale) {
}
