package com.kenyarealestate.verification.photo;

import java.util.List;
import java.util.Objects;

/**
 * What the system concluded about one listing photograph.
 *
 * @param subject        what the image appears to depict
 * @param confidence     0-100
 * @param perceptualHash dHash, for cross-listing comparison
 * @param concerns       everything found worth a human's attention
 */
public record PhotoVerdict(
        Subject subject,
        int confidence,
        long perceptualHash,
        List<Concern> concerns) {

    public PhotoVerdict {
        Objects.requireNonNull(subject, "subject");
        concerns = List.copyOf(Objects.requireNonNull(concerns, "concerns"));
    }

    /** True when nothing found needs a person to look. */
    public boolean isClean() {
        return concerns.isEmpty();
    }

    /** True when the listing should not go live on this photograph. */
    public boolean isBlocking() {
        return concerns.stream().anyMatch(Concern::blocking);
    }

    public enum Subject {
        /** Exterior of a building, a plot, or a development. */
        PROPERTY_EXTERIOR,
        /** A room, interior fitting, or interior view. */
        PROPERTY_INTERIOR,
        /** A site plan, floor plan or survey drawing. */
        FLOOR_PLAN,
        /** A document photographed and uploaded to the gallery by mistake. */
        DOCUMENT,
        /** A person, an animal, food, a screenshot — anything not a property. */
        NOT_A_PROPERTY,
        /** Could not be read at all. */
        UNREADABLE
    }

    public record Concern(Type type, String detail, boolean blocking) {

        public Concern {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(detail, "detail");
        }

        public enum Type {
            /**
             * The same photograph already appears on a different seller's listing.
             *
             * <p>The single most common listing fraud: lift the gallery from a real
             * advertisement, post it as your own, collect deposits for a house you have
             * never seen. Blocking, always.
             */
            REUSED_FROM_ANOTHER_LISTING,
            /** The same photograph twice within this listing, padding the gallery. */
            DUPLICATE_WITHIN_LISTING,
            /** Not a property at all. */
            NOT_A_PROPERTY,
            /** A document in the photo gallery — misfiled rather than dishonest. */
            DOCUMENT_IN_GALLERY,
            /** Visible watermark or agency branding belonging to somebody else. */
            THIRD_PARTY_WATERMARK,
            /** Signs of compositing or editing of the structure itself. */
            APPEARS_EDITED,
            /** Too small or too blurred to show what is being sold. */
            TOO_LOW_QUALITY
        }
    }
}
