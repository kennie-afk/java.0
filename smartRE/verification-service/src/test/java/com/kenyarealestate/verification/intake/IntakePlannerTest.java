package com.kenyarealestate.verification.intake;

import static org.assertj.core.api.Assertions.assertThat;

import com.kenyarealestate.verification.intake.IntakePlan.ReviewItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("bulk intake planning")
class IntakePlannerTest {

    private final IntakePlanner planner = new IntakePlanner();

    private static final Set<String> OWNERSHIP_REQUIRED =
            Set.of("TITLE_DEED", "LAND_SEARCH_CERTIFICATE");

    @Nested
    @DisplayName("the case this exists for")
    class HappyPath {

        @Test
        void filesAWholeBatchWithNobodyTouchingADropdown() {
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "IMG_4021.jpg", "h1", "TITLE_DEED", 96),
                    upload("b", "scan002.pdf", "h2", "LAND_SEARCH_CERTIFICATE", 91),
                    upload("c", "rates.pdf", "h3", "RATES_CLEARANCE_CERTIFICATE", 88)),
                    OWNERSHIP_REQUIRED, false);

            assertThat(plan.fullyAutomatic()).isTrue();
            assertThat(plan.filedCount()).isEqualTo(3);
            assertThat(plan.filed()).containsOnlyKeys(
                    "TITLE_DEED", "LAND_SEARCH_CERTIFICATE", "RATES_CLEARANCE_CERTIFICATE");
        }

        @Test
        void filenamesAreIgnoredEntirely() {
            // A file called "title deed.pdf" that is actually a rates clearance must be
            // filed as a rates clearance. The name is the seller's assertion; the
            // contents are the evidence.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "title deed.pdf", "h1", "RATES_CLEARANCE_CERTIFICATE", 94)),
                    Set.of(), false);

            assertThat(plan.filed()).containsOnlyKeys("RATES_CLEARANCE_CERTIFICATE");
        }
    }

    @Nested
    @DisplayName("multi-page documents are normal, not suspicious")
    class MultiPage {

        @Test
        void keepsEveryPageOfATitleDeed() {
            // A Kenyan title deed runs to several pages. Treating the second page as a
            // conflict would teach sellers to upload less than they should.
            IntakePlan plan = planner.plan(List.of(
                    upload("p1", "deed-1.jpg", "h1", "TITLE_DEED", 95),
                    upload("p2", "deed-2.jpg", "h2", "TITLE_DEED", 93),
                    upload("p3", "deed-3.jpg", "h3", "TITLE_DEED", 90)),
                    Set.of("TITLE_DEED"), false);

            assertThat(plan.needsReview()).isEmpty();
            assertThat(plan.filed().get("TITLE_DEED")).hasSize(3);
        }

        @Test
        void pagesStayInUploadOrderNotConfidenceOrder() {
            // Page 1 must read first even if the classifier was surer about page 3.
            IntakePlan plan = planner.plan(List.of(
                    upload("p1", "deed-1.jpg", "h1", "TITLE_DEED", 80),
                    upload("p2", "deed-2.jpg", "h2", "TITLE_DEED", 99)),
                    Set.of(), false);

            assertThat(plan.filed().get("TITLE_DEED"))
                    .extracting(ClassifiedUpload::uploadRef)
                    .containsExactly("p1", "p2");
        }

        @Test
        void twoDifferentIdCardsAreNotTwoPages() {
            // NATIONAL_ID_FRONT holds exactly one document. A second distinct one is a
            // second person's card, and that is worth stopping.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "id-front.jpg", "h1", "NATIONAL_ID_FRONT", 97),
                    upload("b", "other-front.jpg", "h2", "NATIONAL_ID_FRONT", 95)),
                    Set.of(), true);

            assertThat(plan.filed().get("NATIONAL_ID_FRONT")).hasSize(1);
            assertThat(plan.needsReview()).singleElement()
                    .satisfies(item -> {
                        assertThat(item.reason()).isEqualTo(ReviewItem.Reason.SLOT_ALREADY_FILLED);
                        assertThat(item.upload().uploadRef()).isEqualTo("b");
                    });
        }

        @Test
        void theSurerDocumentTakesASingleSlotRegardlessOfUploadOrder() {
            IntakePlan plan = planner.plan(List.of(
                    upload("weaker", "a.jpg", "h1", "NATIONAL_ID_FRONT", 78),
                    upload("stronger", "b.jpg", "h2", "NATIONAL_ID_FRONT", 98)),
                    Set.of(), true);

            assertThat(plan.filed().get("NATIONAL_ID_FRONT"))
                    .extracting(ClassifiedUpload::uploadRef)
                    .containsExactly("stronger");
        }
    }

    @Nested
    @DisplayName("what the system refuses to guess at")
    class Refusals {

        @Test
        void aLowConfidenceAnswerIsNeverFiled() {
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "blurry.jpg", "h1", "TITLE_DEED", 61)),
                    Set.of("TITLE_DEED"), false);

            assertThat(plan.filed()).isEmpty();
            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.LOW_CONFIDENCE);
            assertThat(plan.stillMissing()).containsExactly("TITLE_DEED");
        }

        @Test
        void theSameFileTwiceIsFiledOnce() {
            // Otherwise one deed becomes corroborating evidence for itself.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "deed.pdf", "same-hash", "TITLE_DEED", 95),
                    upload("b", "deed-copy.pdf", "same-hash", "TITLE_DEED", 95)),
                    Set.of(), false);

            assertThat(plan.filed().get("TITLE_DEED")).hasSize(1);
            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.DUPLICATE_CONTENT);
        }

        @Test
        void aRandomPhotographIsNotFiledAnywhere() {
            // Somebody uploads a picture of their lunch. It must not land in a slot.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "IMG_2231.jpg", "h1", "UNKNOWN", 99)),
                    Set.of("TITLE_DEED"), false);

            assertThat(plan.filed()).isEmpty();
            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.NOT_A_RECOGNISED_CATEGORY);
        }

        @Test
        void anIdentityDocumentIsNotAcceptedIntoAnOwnershipBatch() {
            // Right document, wrong step. A KRA PIN certificate is a real category, but
            // not one the ownership flow accepts.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "kra.pdf", "h1", "KRA_PIN_CERTIFICATE", 98)),
                    Set.of(), false);

            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.NOT_A_RECOGNISED_CATEGORY);
        }

        @Test
        void anUnreadableFileIsSurfacedRatherThanDropped() {
            IntakePlan plan = planner.plan(List.of(
                    ClassifiedUpload.failed("a", "corrupt.pdf", "h1")),
                    Set.of(), false);

            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.UNREADABLE);
        }

        @Test
        void confidenceIsJudgedBeforeAnythingElse() {
            // A low-confidence duplicate is reported as low confidence, not as a
            // duplicate: acting on the classification at all — even to reject it —
            // would treat a guess as a fact.
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "deed.pdf", "same", "TITLE_DEED", 95),
                    upload("b", "copy.pdf", "same", "TITLE_DEED", 40)),
                    Set.of(), false);

            assertThat(plan.needsReview()).singleElement()
                    .extracting(ReviewItem::reason)
                    .isEqualTo(ReviewItem.Reason.LOW_CONFIDENCE);
        }
    }

    @Nested
    @DisplayName("telling the seller what is still needed")
    class Completeness {

        @Test
        void namesEveryRequiredCategoryNothingWasFiledAgainst() {
            IntakePlan plan = planner.plan(List.of(
                    upload("a", "deed.pdf", "h1", "TITLE_DEED", 95)),
                    Set.of("TITLE_DEED", "LAND_SEARCH_CERTIFICATE", "LAND_RENT_CLEARANCE"), false);

            assertThat(plan.stillMissing())
                    .containsExactly("LAND_RENT_CLEARANCE", "LAND_SEARCH_CERTIFICATE");
            assertThat(plan.fullyAutomatic()).isFalse();
        }

        @Test
        void anEmptyBatchIsAllMissingAndNoReview() {
            IntakePlan plan = planner.plan(List.of(), OWNERSHIP_REQUIRED, false);

            assertThat(plan.filedCount()).isZero();
            assertThat(plan.needsReview()).isEmpty();
            assertThat(plan.stillMissing()).hasSize(2);
        }
    }

    private static ClassifiedUpload upload(
            String ref, String filename, String sha256, String category, int confidence) {
        return new ClassifiedUpload(ref, filename, sha256, category, confidence, false);
    }
}
