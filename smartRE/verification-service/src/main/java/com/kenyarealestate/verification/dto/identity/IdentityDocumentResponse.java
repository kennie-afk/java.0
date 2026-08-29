package com.kenyarealestate.verification.dto.identity;

import com.kenyarealestate.verification.enums.IdentityDocumentCategory;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class IdentityDocumentResponse {
    private UUID id;
    private IdentityDocumentCategory documentCategory;
    private String documentUrl;
    private Integer aiAuthenticityScore;
    private Boolean aiTamperDetected;
    private Boolean aiSignatureDetected;
    private Boolean aiSealDetected;
    private Boolean aiMetadataClean;
    private Boolean aiFontConsistency;
    private String aiScreeningNotes;
    private String extractedIdNumber;
    private String aiDetectedCategory;
    private Integer aiCategoryConfidence;
    private Boolean aiCategoryMismatch;
    private String aiSideDetected;
    private Boolean humanVerified;
    private String humanReviewNotes;
    private LocalDateTime uploadedAt;
}
