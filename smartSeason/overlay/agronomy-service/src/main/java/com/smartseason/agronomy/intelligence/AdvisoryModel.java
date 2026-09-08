package com.smartseason.agronomy.intelligence;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;

/**
 * Anything that can answer an agronomic question.
 *
 * Two implementations exist: one calls a vision model, the other applies
 * deterministic agronomic rules. The rules implementation is not a stub - it is
 * the answer when no model is configured, and it is what the model's output is
 * checked against.
 */
public interface AdvisoryModel {

    /** MODEL or RULES; carried into every response so nobody has to guess. */
    String source();

    DiagnosisResponse diagnose(DiagnosisRequest request);

    SeasonForecastResponse forecastSeason(SeasonForecastRequest request);

    WeatherOutlookResponse weatherOutlook(WeatherOutlookRequest request);
}
