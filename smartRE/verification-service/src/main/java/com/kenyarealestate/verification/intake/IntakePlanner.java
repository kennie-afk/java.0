package com.kenyarealestate.verification.intake;

import com.kenyarealestate.verification.intake.IntakePlan.ReviewItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a bag of classified files into a filing plan, with no human in the loop for
 * anything the system is sure about.
 *
 * <p>Pure: no database, no network, no clock. Every decision here is a consequence of
 * the inputs, which is what lets the interesting cases — a duplicate, two different ID
 * cards, an unreadable scan — be tested exhaustively rather than hoped about.
 *
 * <p>The ordering of checks matters and is not arbitrary. Confidence is judged before
 * anything else, because acting on a low-confidence answer at all — even to reject it as
 * a duplicate — treats a guess as a fact.
 */
public final class IntakePlanner {

    /**
     * Below this, the classifier's answer is recorded but not acted on.
     *
     * <p>Set at 75 rather than 50. The cost of the two errors is not symmetric: an
     * unfiled document costs a seller ten seconds with a dropdown, while a misfiled one
     * reaches a reviewer wearing the wrong label and may be approved as something it is
     * not. When in doubt, ask.
     */
    public static final int CONFIDENCE_THRESHOLD = 75;

    private final int confidenceThreshold;

    public IntakePlanner() {
        this(CONFIDENCE_THRESHOLD);
    }

    public IntakePlanner(int confidenceThreshold) {
        if (confidenceThreshold < 0 || confidenceThreshold > 100) {
            throw new IllegalArgumentException("threshold must be 0-100, got " + confidenceThreshold);
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * @param uploads            the batch, already classified
     * @param requiredCategories what this verification type cannot proceed without
     * @param identity           true for seller identity, false for property ownership
     */
    public IntakePlan plan(
            List<ClassifiedUpload> uploads, Set<String> requiredCategories, boolean identity) {

        Objects.requireNonNull(uploads, "uploads");
        Objects.requireNonNull(requiredCategories, "requiredCategories");

        Map<String, List<ClassifiedUpload>> filed = new LinkedHashMap<>();
        List<ReviewItem> review = new ArrayList<>();
        Set<String> seenDigests = new HashSet<>();

        // Highest confidence first. When two files compete for a single-occupancy slot,
        // the one the classifier was surer about takes it and the other is surfaced —
        // rather than the winner being decided by upload order, which is arbitrary.
        List<ClassifiedUpload> ordered = uploads.stream()
                .sorted(Comparator.comparingInt(ClassifiedUpload::confidence).reversed())
                .toList();

        for (ClassifiedUpload upload : ordered) {

            if (upload.analysisFailed()) {
                review.add(new ReviewItem(upload, ReviewItem.Reason.UNREADABLE,
                        "Could not be read as an image or a PDF."));
                continue;
            }

            if (upload.confidence() < confidenceThreshold) {
                review.add(new ReviewItem(upload, ReviewItem.Reason.LOW_CONFIDENCE,
                        "Looks like %s, but only %d%% sure.".formatted(
                                describe(upload.detectedCategory()), upload.confidence())));
                continue;
            }

            String category = upload.detectedCategory();

            if (!DocumentSlot.isKnown(category, identity)) {
                review.add(new ReviewItem(upload, ReviewItem.Reason.NOT_A_RECOGNISED_CATEGORY,
                        "Does not look like any document this step accepts."));
                continue;
            }

            // Content duplicates, not filename duplicates. The same photograph uploaded
            // twice under different names is one document, and filing it twice would
            // make a single deed look like corroborating evidence for itself.
            if (upload.sha256() != null && !seenDigests.add(upload.sha256())) {
                review.add(new ReviewItem(upload, ReviewItem.Reason.DUPLICATE_CONTENT,
                        "Identical to another file in this upload."));
                continue;
            }

            List<ClassifiedUpload> slot = filed.computeIfAbsent(category, k -> new ArrayList<>());

            if (!slot.isEmpty() && !DocumentSlot.acceptsMultiple(category, identity)) {
                review.add(new ReviewItem(upload, ReviewItem.Reason.SLOT_ALREADY_FILLED,
                        "A different document was already filed as %s."
                                .formatted(describe(category))));
                continue;
            }

            slot.add(upload);
        }

        List<String> missing = requiredCategories.stream()
                .filter(required -> !filed.containsKey(required))
                .sorted()
                .toList();

        // Restore upload order within each slot, so a multi-page deed reads page 1
        // first rather than in descending confidence.
        Map<Integer, Integer> position = new HashMap<>();
        for (int i = 0; i < uploads.size(); i++) {
            position.put(System.identityHashCode(uploads.get(i)), i);
        }
        filed.replaceAll((category, docs) -> docs.stream()
                .sorted(Comparator.comparingInt(d -> position.getOrDefault(System.identityHashCode(d), 0)))
                .toList());

        return new IntakePlan(filed, review, missing);
    }

    /** Turns SCREAMING_SNAKE into something a seller can read. */
    private static String describe(String category) {
        if (category == null || category.isBlank()) {
            return "an unrecognised document";
        }
        String[] words = category.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word);
        }
        String readable = out.toString();
        return readable.substring(0, 1).toUpperCase() + readable.substring(1);
    }
}
