package com.smartseason.agronomy.intelligence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class AdvisoryDtos {

    private AdvisoryDtos() {
    }

    /**
     * A photograph submitted for scanning. Base64 keeps the request a single
     * JSON body; anything larger than a few megabytes is refused rather than
     * streamed, because a field photo from a phone is well under that.
     */
    public record ImagePayload(
            @NotBlank String mediaType,
            @NotBlank String base64) {
    }

    public record DiagnosisRequest(
            @NotBlank @Size(max = 64) String cropCode,
            @Size(max = 4000) String symptoms,
            List<ImagePayload> images,
            String county,
            String growthStage) {
    }

    /** One candidate cause, most likely first. */
    public record Candidate(
            String code,
            String commonName,
            String scientificName,
            double confidence,
            String reasoning) {
    }

    public record DiagnosisResponse(
            List<Candidate> candidates,
            String severity,
            String management,
            String immediateAction,
            /** MODEL when a vision model answered, RULES when the fallback did. */
            String source,
            List<String> caveats) {
    }

    public record SeasonForecastRequest(
            @NotBlank @Size(max = 64) String cropCode,
            String variety,
            @NotBlank String startDate,
            Double areaHa,
            String county,
            Double expectedYieldKg,
            String currentStage,
            Double recentRainfallMm,
            Double averageTempC) {
    }

    public record StageOutlook(
            String stage,
            LocalDate expectedStart,
            LocalDate expectedEnd,
            String guidance) {
    }

    public record SeasonForecastResponse(
            LocalDate expectedHarvestDate,
            Double projectedYieldKg,
            Double confidence,
            List<StageOutlook> stages,
            List<String> risks,
            String summary,
            String source) {
    }

    public record WeatherOutlookRequest(
            @NotBlank String county,
            String cropCode,
            String growthStage,
            Double rainfallLast7DaysMm,
            Double averageTempC,
            Double soilMoisturePct) {
    }

    public record WeatherOutlookResponse(
            String outlook,
            List<String> advisories,
            String irrigationAdvice,
            String sprayWindow,
            String source) {
    }
}
