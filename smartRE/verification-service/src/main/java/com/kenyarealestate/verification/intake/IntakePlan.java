package com.kenyarealestate.verification.intake;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the system proposes to do with a bulk upload.
 *
 * <p>A plan, not an action. Everything the system is confident about is filed; everything
 * it is not is surfaced with the reason. The seller sees one screen showing what was
 * filed and the short list of what still needs a human — instead of a form with
 * twenty-five dropdowns.
 *
 * <p>There is deliberately no "best guess" bucket. A document assigned on a 40% hunch is
 * worse than one left unassigned, because the seller will not re-check something the
 * system already appears to have handled, and a misfiled title deed reaches a reviewer
 * looking like a verified one.
 *
 * @param filed        category to the documents placed in it, in upload order
 * @param needsReview  documents the system would not file, each with a reason
 * @param stillMissing required categories nothing was filed against
 */
public record IntakePlan(
        Map<String, List<ClassifiedUpload>> filed,
        List<ReviewItem> needsReview,
        List<String> stillMissing) {

    public IntakePlan {
        filed = Map.copyOf(Objects.requireNonNull(filed, "filed"));
        needsReview = List.copyOf(Objects.requireNonNull(needsReview, "needsReview"));
        stillMissing = List.copyOf(Objects.requireNonNull(stillMissing, "stillMissing"));
    }

    public int filedCount() {
        return filed.values().stream().mapToInt(List::size).sum();
    }

    /** True when the batch went in without needing anyone to touch a dropdown. */
    public boolean fullyAutomatic() {
        return needsReview.isEmpty() && stillMissing.isEmpty();
    }

    public record ReviewItem(ClassifiedUpload upload, Reason reason, String detail) {

        public ReviewItem {
            Objects.requireNonNull(upload, "upload");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }

        public enum Reason {
            /** The classifier was not sure enough to act on. */
            LOW_CONFIDENCE,
            /** Byte-for-byte the same file as one already filed in this batch. */
            DUPLICATE_CONTENT,
            /**
             * A second distinct document for a category that holds only one — two
             * different national ID fronts, not two pages of one deed.
             */
            SLOT_ALREADY_FILLED,
            /** Classified as something this verification type does not accept. */
            NOT_A_RECOGNISED_CATEGORY,
            /** Could not be read as an image or a PDF at all. */
            UNREADABLE
        }
    }
}
