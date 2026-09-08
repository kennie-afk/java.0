package com.smartseason.agronomy.intelligence;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;
import com.smartseason.agronomy.platform.DomainRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agronomy/v1/intelligence")
@Tag(name = "Intelligence", description = "Diagnosis from photographs and text, season and weather outlooks")
public class IntelligenceController {

    /** Roughly 5MB of image once base64 expansion is taken off. */
    private static final int MAX_BASE64_CHARS = 7_000_000;

    private final AdvisoryModel model;

    public IntelligenceController(AdvisoryModel model) {
        this.model = model;
    }

    @PostMapping("/diagnose")
    @Operation(summary = "Identify a pest or disease from symptoms and photographs")
    public DiagnosisResponse diagnose(@Valid @RequestBody DiagnosisRequest request) {
        boolean hasText = request.symptoms() != null && !request.symptoms().isBlank();
        boolean hasImages = request.images() != null && !request.images().isEmpty();
        if (!hasText && !hasImages) {
            throw new DomainRuleException("Describe the symptoms or attach a photograph");
        }
        if (hasImages) {
            for (AdvisoryDtos.ImagePayload image : request.images()) {
                if (image.base64().length() > MAX_BASE64_CHARS) {
                    throw new DomainRuleException("Each photograph must be under 5MB");
                }
                if (!image.mediaType().startsWith("image/")) {
                    throw new DomainRuleException("Only images can be attached");
                }
            }
        }
        return model.diagnose(request);
    }

    @PostMapping("/forecast-season")
    @Operation(summary = "Project harvest date, yield and stage calendar for a season")
    public SeasonForecastResponse forecastSeason(@Valid @RequestBody SeasonForecastRequest request) {
        return model.forecastSeason(request);
    }

    @PostMapping("/weather-outlook")
    @Operation(summary = "Turn recent weather into irrigation and spraying advice")
    public WeatherOutlookResponse weatherOutlook(@Valid @RequestBody WeatherOutlookRequest request) {
        return model.weatherOutlook(request);
    }
}
