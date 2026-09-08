package com.kenyarealestate.verification.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.kenyarealestate.verification.photo.GalleryAssessor.IncomingPhoto;
import com.kenyarealestate.verification.photo.PhotoVerdict.Concern;
import com.kenyarealestate.verification.photo.PhotoVerdict.Subject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("listing gallery assessment")
class GalleryAssessorTest {

    private final GalleryAssessor assessor = new GalleryAssessor();

    @Nested
    @DisplayName("the fraud this exists to stop")
    class StolenPhotos {

        @Test
        void blocksAPhotographTakenFromAnotherSellersListing() {
            long stolen = 0x0F0F_0F0F_0F0F_0F0FL;
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.PROPERTY_EXTERIOR, 95, stolen)),
                    Map.of(stolen, "LISTING-4471"));

            assertThat(verdicts).singleElement().satisfies(v -> {
                assertThat(v.isBlocking()).isTrue();
                assertThat(v.concerns()).extracting(Concern::type)
                        .contains(Concern.Type.REUSED_FROM_ANOTHER_LISTING);
                assertThat(v.concerns().get(0).detail()).contains("LISTING-4471");
            });
            assertThat(assessor.galleryMayPublish(verdicts)).isFalse();
        }

        @Test
        void catchesAReCompressedCopyNotJustAnExactOne() {
            // The whole point: a content digest would miss this, because re-saving the
            // image changes every byte while leaving the picture the same.
            long original = 0x0F0F_0F0F_0F0F_0F0FL;
            long reCompressed = original ^ 0b111L;   // three bits differ

            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.PROPERTY_EXTERIOR, 95, reCompressed)),
                    Map.of(original, "LISTING-4471"));

            assertThat(verdicts.get(0).isBlocking()).isTrue();
        }

        @Test
        void oneStolenPhotographStopsTheWholeGallery() {
            // Publishing the rest and silently dropping the stolen one tells a fraudster
            // exactly which image was recognised.
            long stolen = 0x0F0F_0F0F_0F0F_0F0FL;
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("good1", Subject.PROPERTY_EXTERIOR, 96, 0xFFFF_FFFF_0000_0000L),
                            photo("stolen", Subject.PROPERTY_EXTERIOR, 94, stolen),
                            photo("good2", Subject.PROPERTY_INTERIOR, 93, 0x0000_0000_FFFF_FFFFL)),
                    Map.of(stolen, "LISTING-4471"));

            assertThat(assessor.galleryMayPublish(verdicts)).isFalse();
            assertThat(verdicts.get(0).isClean()).isTrue();
            assertThat(verdicts.get(2).isClean()).isTrue();
        }

        @Test
        void aDifferentBuildingIsNotFlagged() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.PROPERTY_EXTERIOR, 95, 0xFFFF_0000_FFFF_0000L)),
                    Map.of(0x0000_FFFF_0000_FFFFL, "LISTING-4471"));

            assertThat(verdicts.get(0).isClean()).isTrue();
        }
    }

    @Nested
    @DisplayName("what is in the gallery")
    class Subjects {

        @Test
        void blocksAPhotographThatIsNotAPropertyAtAll() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.NOT_A_PROPERTY, 92, 0x1L)), Map.of());

            assertThat(verdicts.get(0).isBlocking()).isTrue();
            assertThat(verdicts.get(0).concerns()).extracting(Concern::type)
                    .containsExactly(Concern.Type.NOT_A_PROPERTY);
        }

        @Test
        void doesNotBlockOnALowConfidenceNotAPropertyCall() {
            // Rejecting a genuine photograph strands an honest seller with no remedy,
            // so the benefit of the doubt runs the other way from documents.
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.NOT_A_PROPERTY, 45, 0x1L)), Map.of());

            assertThat(verdicts.get(0).isBlocking()).isFalse();
            assertThat(verdicts.get(0).isClean()).isTrue();
        }

        @Test
        void flagsADocumentInThePhotoGalleryWithoutBlocking() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.DOCUMENT, 97, 0x1L)), Map.of());

            assertThat(verdicts.get(0).isBlocking()).isFalse();
            assertThat(verdicts.get(0).concerns()).extracting(Concern::type)
                    .containsExactly(Concern.Type.DOCUMENT_IN_GALLERY);
        }

        @Test
        void acceptsInteriorsFloorPlansAndExteriors() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("a", Subject.PROPERTY_EXTERIOR, 95, 0x0000_0000_0000_0000L),
                            photo("b", Subject.PROPERTY_INTERIOR, 95, 0xFFFF_FFFF_0000_0000L),
                            photo("c", Subject.FLOOR_PLAN, 95, 0x0000_0000_FFFF_FFFFL)),
                    Map.of());

            assertThat(verdicts).allMatch(PhotoVerdict::isClean);
            assertThat(assessor.galleryMayPublish(verdicts)).isTrue();
        }
    }

    @Nested
    @DisplayName("quality and padding")
    class Quality {

        @Test
        void flagsTheSamePhotographUsedTwiceToPadTheGallery() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.PROPERTY_EXTERIOR, 95, 0xABCDL),
                            photo("p2", Subject.PROPERTY_EXTERIOR, 95, 0xABCDL)),
                    Map.of());

            assertThat(verdicts.get(0).isClean()).isTrue();
            assertThat(verdicts.get(1).concerns()).extracting(Concern::type)
                    .containsExactly(Concern.Type.DUPLICATE_WITHIN_LISTING);
            // Padding is untidy, not fraudulent — it does not stop publication.
            assertThat(assessor.galleryMayPublish(verdicts)).isTrue();
        }

        @Test
        void flagsAnImageTooSmallToShowAnything() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(new IncomingPhoto("p1", Subject.PROPERTY_EXTERIOR, 95, 0x1L,
                            240, null, false)),
                    Map.of());

            assertThat(verdicts.get(0).concerns()).extracting(Concern::type)
                    .contains(Concern.Type.TOO_LOW_QUALITY);
        }

        @Test
        void reportsAnotherAgencysWatermarkWithoutBlockingOnItsOwn() {
            // An agency may legitimately watermark its own listings. Combined with a
            // reuse match it becomes a complete story, but alone it is not proof.
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(new IncomingPhoto("p1", Subject.PROPERTY_EXTERIOR, 95, 0x1L,
                            1200, "Pam Golding", false)),
                    Map.of());

            assertThat(verdicts.get(0).isBlocking()).isFalse();
            assertThat(verdicts.get(0).concerns()).extracting(Concern::type)
                    .containsExactly(Concern.Type.THIRD_PARTY_WATERMARK);
        }

        @Test
        void blocksAnUnreadableImage() {
            List<PhotoVerdict> verdicts = assessor.assess(
                    List.of(photo("p1", Subject.UNREADABLE, 0, 0x0L)), Map.of());

            assertThat(verdicts.get(0).isBlocking()).isTrue();
        }
    }

    @Test
    void anEmptyGalleryPublishesWithoutConcerns() {
        List<PhotoVerdict> verdicts = assessor.assess(List.of(), Map.of());
        assertThat(verdicts).isEmpty();
        assertThat(assessor.galleryMayPublish(verdicts)).isTrue();
    }

    @Test
    @DisplayName("the fixtures used above really are different images")
    void fixtureHashesAreFarEnoughApart() {
        // Guards the tests, not the code. Toy values like 0x1 and 0x2 differ by two
        // bits, which is well inside DUPLICATE_THRESHOLD — so a gallery built from them
        // would be flagged as duplicates and every other assertion would be vacuous.
        long a = 0x0000_0000_0000_0000L;
        long b = 0xFFFF_FFFF_0000_0000L;
        long c = 0x0000_0000_FFFF_FFFFL;

        assertThat(PerceptualHash.distance(a, b)).isGreaterThan(PerceptualHash.DUPLICATE_THRESHOLD);
        assertThat(PerceptualHash.distance(b, c)).isGreaterThan(PerceptualHash.DUPLICATE_THRESHOLD);
        assertThat(PerceptualHash.distance(a, c)).isGreaterThan(PerceptualHash.DUPLICATE_THRESHOLD);
    }

    private static IncomingPhoto photo(String ref, Subject subject, int confidence, long hash) {
        return new IncomingPhoto(ref, subject, confidence, hash, 1200, null, false);
    }
}
