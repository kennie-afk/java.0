package com.smartseason.fraud.web.dto;

import com.smartseason.fraud.domain.FraudRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FraudRuleUpdateRequest(
        @Size(max = 255) String code,
        FraudRule.Typology typology,
        @Size(max = 255) String name,
        String description,
        String expression,
        BigDecimal threshold,
        FraudRule.Severity severity,
        Integer weight,
        Boolean enabled,
        Boolean autoHoldPayout) {
}
