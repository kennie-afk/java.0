package com.hms.claims;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClaimModels {
    private ClaimModels() {}

    public record AssembleInput(@NotNull UUID invoiceId) {}

    public record WithdrawInput(@NotBlank @Size(min = 5, max = 300) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Issue(String ruleCode, String severity, String field, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Submission(UUID id, String adapter, boolean verified, boolean sent, String outcome, String detail, Instant submittedAt, UUID submittedBy) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Claim(UUID id, UUID facilityId, UUID patientId, String patientName, UUID encounterId, UUID invoiceId, String claimNumber, String payerType, String status,
                        BigDecimal total, Instant assembledAt, String withdrawReason, int version, List<Issue> issues, Map<String, Object> bundle, List<Submission> submissions,
                        String disclaimer) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(UUID id, String claimNumber, UUID patientId, String patientName, String status, BigDecimal total, int errors, int warnings, Instant assembledAt) {}

    public record RuleCount(String ruleCode, String severity, long claims) {}

    public record Summary(Map<String, Long> byStatus, List<RuleCount> topIssues) {}
}
