package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.GradeStandard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GradeStandardCreateRequest(
        @NotBlank @Size(max = 255) String commodityCode,
        @NotBlank @Size(max = 255) String grade,
        @NotBlank String criteria,
        BigDecimal minSizeMm,
        BigDecimal maxDefectPct,
        BigDecimal moisturePctMax,
        @NotNull Integer revision) {
}
