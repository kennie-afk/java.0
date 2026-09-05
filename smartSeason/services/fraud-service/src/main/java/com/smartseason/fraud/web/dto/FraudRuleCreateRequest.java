package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FraudRuleCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotNull FraudRule.Typology typology,
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotBlank String expression,
        BigDecimal threshold,
        @NotNull FraudRule.Severity severity,
        @NotNull Integer weight,
        @NotNull Boolean enabled,
        @NotNull Boolean autoHoldPayout) {
}
