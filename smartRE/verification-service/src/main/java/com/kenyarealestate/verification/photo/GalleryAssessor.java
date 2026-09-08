package com.kenyarealestate.verification.photo;

import com.kenyarealestate.verification.photo.PhotoVerdict.Concern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides what is wrong with a listing's photographs.
 *
 * <p>Pure. The classifier and the hash index are supplied as inputs, so every rule here
 * is testable without a model or a database — which matters, because these rules decide
 * whether a listing goes live.
 */
public final class GalleryAssessor {

    /**
     * Below this, a subject classification is not acted on.
     *
     * <p>Lower than the document threshold of 75 deliberately. Rejecting a genuine
     * photograph of a house strands an honest seller with no obvious remedy, whereas an
     * unusual-looking photograph that reaches a reviewer costs a few seconds. The
     * asymmetry runs the other way from documents.
     */
    public static final int SUBJECT_CONFIDENCE_THRESHOLD = 60;

    /** Smallest edge, in pixels, that still shows what is being sold. */
    public static final int MIN_EDGE_PIXELS = 480;

    /**
     * One photograph as it arrives, already classified.
     *
     * @param photoRef       handle within the upload
     * @param subject        what the classifier says it depicts
     * @param confidence     0-100
     * @param perceptualHash dHash of the image
     * @param smallestEdge   shortest side in pixels
     * @param watermarkText  third-party branding found, or null
     * @param appearsEdited  compositing or structural editing suspected
     */
    public record IncomingPhoto(
            String photoRef,
            PhotoVerdict.Subject subject,
            int confidence,
            long perceptualHash,
            int smallestEdge,
            String watermarkText,
            boolean appearsEdited) {

        public IncomingPhoto {
            Objects.requireNonNull(photoRef, "photoRef");
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * @param photos        the gallery being uploaded
     * @param knownElsewhere perceptual hashes already on OTHER sellers' live listings,
     *                       mapped to the listing they belong to
     */
    public List<PhotoVerdict> assess(List<IncomingPhoto> photos, Map<Long, String> knownElsewhere) {
        Objects.requireNonNull(photos, "photos");
        Objects.requireNonNull(knownElsewhere, "knownElsewhere");

        List<PhotoVerdict> verdicts = new ArrayList<>(photos.size());
        List<Long> seenInThisGallery = new ArrayList<>();

        for (IncomingPhoto photo : photos) {
            List<Concern> concerns = new ArrayList<>();

            // Checked first and hardest. Everything else on this list is a quality
            // problem; this one is somebody selling a house they have never been to.
            String stolenFrom = matchElsewhere(photo.perceptualHash(), knownElsewhere);
            if (stolenFrom != null) {
                concerns.add(new Concern(Concern.Type.REUSED_FROM_ANOTHER_LISTING,
                        "Already published on listing " + stolenFrom + ".", true));
            }

            if (seenInThisGallery.stream()
                    .anyMatch(seen -> PerceptualHash.looksLikeSameImage(seen, photo.perceptualHash()))) {
                concerns.add(new Concern(Concern.Type.DUPLICATE_WITHIN_LISTING,
                        "The same photograph appears more than once in this gallery.", false));
            }
            seenInThisGallery.add(photo.perceptualHash());

            switch (photo.subject()) {
                case NOT_A_PROPERTY -> {
                    if (photo.confidence() >= SUBJECT_CONFIDENCE_THRESHOLD) {
                        concerns.add(new Concern(Concern.Type.NOT_A_PROPERTY,
                                "Does not appear to show a property.", true));
                    }
                }
                case DOCUMENT -> concerns.add(new Concern(Concern.Type.DOCUMENT_IN_GALLERY,
                        "Looks like a document. Upload it under verification instead.", false));
                case UNREADABLE -> concerns.add(new Concern(Concern.Type.TOO_LOW_QUALITY,
                        "Could not be read as an image.", true));
                default -> {
                    // A property photograph. Nothing to add on subject alone.
                }
            }

            if (photo.smallestEdge() > 0 && photo.smallestEdge() < MIN_EDGE_PIXELS) {
                concerns.add(new Concern(Concern.Type.TOO_LOW_QUALITY,
                        "Only %dpx on its shortest side; too small to show the property."
                                .formatted(photo.smallestEdge()), false));
            }

            if (photo.watermarkText() != null && !photo.watermarkText().isBlank()) {
                // Not blocking on its own — an agency may legitimately watermark its own
                // listings — but a watermark plus a reuse match is a complete story.
                concerns.add(new Concern(Concern.Type.THIRD_PARTY_WATERMARK,
                        "Carries the mark of " + photo.watermarkText().trim() + ".", false));
            }

            if (photo.appearsEdited()) {
                concerns.add(new Concern(Concern.Type.APPEARS_EDITED,
                        "Shows signs of editing to the structure itself.", false));
            }

            verdicts.add(new PhotoVerdict(
                    photo.subject(), photo.confidence(), photo.perceptualHash(), concerns));
        }

        return verdicts;
    }

    /**
     * Whether the whole gallery may go live.
     *
     * <p>One blocking photograph stops the listing rather than being silently dropped.
     * Quietly removing a stolen photograph and publishing the rest tells a fraudster
     * exactly which of their images was recognised.
     */
    public boolean galleryMayPublish(List<PhotoVerdict> verdicts) {
        return verdicts.stream().noneMatch(PhotoVerdict::isBlocking);
    }

    private static String matchElsewhere(long hash, Map<Long, String> knownElsewhere) {
        for (Map.Entry<Long, String> entry : knownElsewhere.entrySet()) {
            if (PerceptualHash.looksLikeSameImage(hash, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
