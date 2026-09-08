package com.kenyarealestate.verification.service;

import com.kenyarealestate.verification.dto.intake.BulkIntakeRequest;
import com.kenyarealestate.verification.dto.intake.BulkIntakeResponse;
import com.kenyarealestate.verification.dto.ownership.UploadOwnershipDocumentRequest;
import com.kenyarealestate.verification.enums.OwnershipDocumentCategory;
import com.kenyarealestate.verification.intake.ClassifiedUpload;
import com.kenyarealestate.verification.intake.IntakePlan;
import com.kenyarealestate.verification.intake.IntakePlanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Files a folder of documents without asking the seller what any of them are.
 *
 * <p>Three steps, deliberately separated: classify every file (in parallel, because each
 * is an independent model call), plan the filing (pure, and therefore exhaustively
 * tested), then apply the plan through the existing single-document path so that every
 * document still goes through the same authenticity analysis, fraud checks and audit
 * trail as one uploaded by hand. Bulk intake changes how a category is chosen. It does
 * not create a second, weaker route into the system.
 */
@Slf4j
@Service
public class BulkIntakeService {

    /** Ownership cannot proceed without these, whatever else is supplied. */
    private static final Set<String> OWNERSHIP_REQUIRED = Set.of(
            OwnershipDocumentCategory.TITLE_DEED.name(),
            OwnershipDocumentCategory.LAND_SEARCH_CERTIFICATE.name());

    private final DocumentIntelligenceService intelligence;
    private final PropertyOwnershipVerificationService ownershipService;
    private final IntakePlanner planner = new IntakePlanner();

    public BulkIntakeService(DocumentIntelligenceService intelligence,
                             PropertyOwnershipVerificationService ownershipService) {
        this.intelligence = intelligence;
        this.ownershipService = ownershipService;
    }

    @Transactional
    public BulkIntakeResponse intakeOwnership(
            UUID userId, UUID verificationId, BulkIntakeRequest request) {

        List<BulkIntakeRequest.Item> items = request.getDocuments();

        // Each classification is an independent network call, so they go out together.
        // Twenty documents one after another is twenty round trips a seller waits through.
        List<CompletableFuture<ClassifiedUpload>> futures = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            futures.add(classifyAsync(items.get(i), "u" + i));
        }

        List<ClassifiedUpload> classified = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        IntakePlan plan = planner.plan(classified, OWNERSHIP_REQUIRED, false);

        // Apply through the ordinary upload path, so nothing skips the checks that
        // path performs.
        Map<String, List<String>> filed = new LinkedHashMap<>();
        plan.filed().forEach((category, docs) -> {
            List<String> filenames = new ArrayList<>(docs.size());
            for (ClassifiedUpload doc : docs) {
                BulkIntakeRequest.Item item = itemFor(items, doc.uploadRef());
                if (item == null) {
                    continue;
                }
                UploadOwnershipDocumentRequest single = new UploadOwnershipDocumentRequest();
                single.setDocumentCategory(OwnershipDocumentCategory.valueOf(category));
                single.setDocumentUrl(item.getDocumentUrl());
                single.setMimeType(item.getMimeType());
                single.setFileSizeBytes(item.getFileSizeBytes());

                ownershipService.uploadDocument(userId, verificationId, single);
                filenames.add(doc.originalFilename());
            }
            if (!filenames.isEmpty()) {
                filed.put(category, filenames);
            }
        });

        List<BulkIntakeResponse.ReviewNeeded> review = plan.needsReview().stream()
                .map(item -> new BulkIntakeResponse.ReviewNeeded(
                        item.upload().originalFilename(),
                        item.reason().name(),
                        item.detail()))
                .toList();

        log.info("bulk intake for verification {}: {} filed, {} for review, {} still missing",
                verificationId, plan.filedCount(), review.size(), plan.stillMissing().size());

        return new BulkIntakeResponse(
                filed, review, plan.stillMissing(), plan.filedCount(), plan.fullyAutomatic());
    }

    private CompletableFuture<ClassifiedUpload> classifyAsync(
            BulkIntakeRequest.Item item, String ref) {
        return intelligence
                .classifyUnclaimedAsync(item.getDocumentUrl(), item.getMimeType(), false)
                .thenApply(result -> {
                    String sha256 = safeDigest(item.getDocumentUrl());
                    if (result.detectedCategory() == null) {
                        return ClassifiedUpload.failed(ref, item.getOriginalFilename(), sha256);
                    }
                    int confidence = result.categoryConfidence() == null ? 0 : result.categoryConfidence();
                    return new ClassifiedUpload(
                            ref, item.getOriginalFilename(), sha256,
                            result.detectedCategory(), confidence, false);
                })
                .exceptionally(e -> {
                    log.warn("classification failed for {}: {}",
                            item.getOriginalFilename(), e.getMessage());
                    return ClassifiedUpload.failed(ref, item.getOriginalFilename(), null);
                });
    }

    /**
     * Content digest for duplicate detection.
     *
     * <p>Returns null rather than throwing when the file cannot be fetched: a document
     * whose digest is unknown simply takes no part in duplicate detection, instead of
     * failing the whole batch.
     */
    private String safeDigest(String documentUrl) {
        try {
            return intelligence.computeSha256FromUrl(documentUrl);
        } catch (Exception e) {
            log.debug("could not digest {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private static BulkIntakeRequest.Item itemFor(List<BulkIntakeRequest.Item> items, String ref) {
        int index = Integer.parseInt(ref.substring(1));
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }
}
