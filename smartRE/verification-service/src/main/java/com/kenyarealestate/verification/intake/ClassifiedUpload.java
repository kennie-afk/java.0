package com.kenyarealestate.verification.intake;

import java.util.Objects;

/**
 * One file in a bulk upload, after the classifier has looked at it and before the
 * system has decided what to do with it.
 *
 * @param uploadRef        opaque handle for this file within the batch
 * @param originalFilename what the seller called it; used only for the review screen,
 *                         never to decide a category — a file named "title deed.pdf"
 *                         proves nothing about its contents
 * @param sha256           content digest, for detecting the same file twice
 * @param detectedCategory what the classifier says this document is
 * @param confidence       0-100; below the threshold the answer is not acted on
 * @param analysisFailed   true when the document could not be read or classified at all
 */
public record ClassifiedUpload(
        String uploadRef,
        String originalFilename,
        String sha256,
        String detectedCategory,
        int confidence,
        boolean analysisFailed) {

    public ClassifiedUpload {
        Objects.requireNonNull(uploadRef, "uploadRef");
        Objects.requireNonNull(originalFilename, "originalFilename");
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("confidence must be 0-100, got " + confidence);
        }
    }

    public static ClassifiedUpload failed(String uploadRef, String filename, String sha256) {
        return new ClassifiedUpload(uploadRef, filename, sha256, null, 0, true);
    }
}
