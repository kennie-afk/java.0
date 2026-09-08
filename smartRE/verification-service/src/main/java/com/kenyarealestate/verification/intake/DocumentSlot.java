package com.kenyarealestate.verification.intake;

import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import java.util.EnumSet;
import java.util.Set;

/**
 * How many documents a category can legitimately hold.
 *
 * <p>This distinction is what makes automatic assignment safe. Without it, two files
 * that both classify as TITLE_DEED look like a conflict — when in practice a Kenyan
 * title deed routinely runs to several pages, and a seller photographing all of them is
 * doing exactly the right thing. Treating that as suspicious would train people to
 * upload less than they should.
 *
 * <p>Conversely, two distinct files both classified NATIONAL_ID_FRONT is not a
 * multi-page document. It is two different identity cards, and that is worth stopping.
 */
public final class DocumentSlot {

    private DocumentSlot() {
    }

    /**
     * Categories that admit exactly one document. Everything else may hold several,
     * because it is a multi-page instrument.
     */
    private static final Set<IdentityDocumentCategory> SINGLE_IDENTITY = EnumSet.of(
            IdentityDocumentCategory.NATIONAL_ID_FRONT,
            IdentityDocumentCategory.NATIONAL_ID_BACK,
            IdentityDocumentCategory.SELFIE_WITH_ID);

    private static final Set<OwnershipDocumentCategory> SINGLE_OWNERSHIP = EnumSet.of(
            OwnershipDocumentCategory.SURVEY_MAP);

    public static boolean acceptsMultiple(String category, boolean identity) {
        if (identity) {
            IdentityDocumentCategory parsed = parseIdentity(category);
            return parsed != null && !SINGLE_IDENTITY.contains(parsed);
        }
        OwnershipDocumentCategory parsed = parseOwnership(category);
        return parsed != null && !SINGLE_OWNERSHIP.contains(parsed);
    }

    /** Whether the classifier's answer is a category this system actually recognises. */
    public static boolean isKnown(String category, boolean identity) {
        return identity ? parseIdentity(category) != null : parseOwnership(category) != null;
    }

    private static IdentityDocumentCategory parseIdentity(String category) {
        try {
            return IdentityDocumentCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static OwnershipDocumentCategory parseOwnership(String category) {
        try {
            return OwnershipDocumentCategory.valueOf(category);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
