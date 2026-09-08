package com.kenyarealestate.verification.dto.intake;

import java.util.List;
import java.util.Map;

/**
 * What the system did with a bulk upload, and what it still needs.
 *
 * @param filed         category to the filenames placed in it
 * @param needsReview   files the system would not file, each with a reason a seller
 *                      can act on
 * @param stillMissing  required categories nothing was filed against
 * @param filedCount    how many documents were filed without human input
 * @param fullyAutomatic true when nobody has to touch a dropdown
 */
public record BulkIntakeResponse(
        Map<String, List<String>> filed,
        List<ReviewNeeded> needsReview,
        List<String> stillMissing,
        int filedCount,
        boolean fullyAutomatic) {

    /**
     * @param reason machine-readable, for the client to group by
     * @param message written for the seller, not the developer
     */
    public record ReviewNeeded(String filename, String reason, String message) {
    }
}
