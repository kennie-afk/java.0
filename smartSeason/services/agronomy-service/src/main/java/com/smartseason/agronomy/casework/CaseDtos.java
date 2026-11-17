package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.domain.Advisory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CaseDtos {

    private CaseDtos() {
    }

    public record PhotoIn(@NotBlank String mediaType, @NotBlank String base64) {
    }

    public record RaiseRequest(
            UUID plotId, UUID seasonId,
            @Size(max = 64) String cropCode, @Size(max = 128) String county,
            @NotBlank @Size(min = 3, max = 4000) String question,
            AdvisoryCase.Reason reason,
            @Size(max = 32) String aiSource, @Size(max = 4000) String aiSummary,
            Double aiConfidence, Advisory.Severity aiSeverity,
            @Valid @Size(max = 3) List<PhotoIn> photos) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 4000) String body) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 200) String title,
                                 @NotBlank @Size(max = 4000) String body,
                                 Advisory.Severity severity) {
    }

    public record AssignRequest(UUID userId) {
    }

    public record ProfileRequest(List<String> counties, Integer maxOpen, Boolean active) {
    }

    public record CaseView(UUID id, String cropCode, String county, String question,
                           AdvisoryCase.Status status, int priority, boolean overdue, Instant dueAt,
                           Instant createdAt, String raisedByName, UUID assignedTo, String assignedToName,
                           String aiSource, Double aiConfidence, Advisory.Severity aiSeverity,
                           AdvisoryCase.Reason reason, int photoCount, boolean unread) {
    }

    public record MessageView(UUID id, UUID authorId, String authorName, String authorRole, String body,
                              Instant at) {
    }

    public record PhotoView(UUID id, String contentType, int sizeBytes) {
    }

    public record CaseDetail(CaseView summary, String fullQuestion, String aiSummary, UUID plotId, UUID seasonId,
                             UUID advisoryId, long similarOpen, List<MessageView> messages,
                             List<PhotoView> photos, boolean canClaim, boolean canReply, boolean canResolve) {
    }

    public record CasePage(List<CaseView> items, String next) {
    }

    public record Summary(long unassigned, long mine, long overdue, long attention) {
    }

    public record ProfileView(UUID userId, String fullName, String email, List<String> counties, int maxOpen,
                              boolean active, long openCases) {
    }
}
