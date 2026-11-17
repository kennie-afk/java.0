package com.smartseason.agronomy.intelligence;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;

/** Anything that can answer an agronomic question. The only real implementation calls Claude. */
public interface AdvisoryModel {

    /** MODEL, or NONE when unconfigured; carried into every response so nobody has to guess. */
    String source();

    DiagnosisResponse diagnose(DiagnosisRequest request);

    SeasonForecastResponse forecastSeason(SeasonForecastRequest request);

    WeatherOutlookResponse weatherOutlook(WeatherOutlookRequest request);
}
