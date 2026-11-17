package com.smartseason.agronomy.intelligence;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;

/** Stands in when no API key is set. Every call is a clear 503, never an invented answer. */
public class UnconfiguredAdvisoryModel implements AdvisoryModel {

    @Override
    public String source() {
        return "NONE";
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        throw AdvisoryUnavailable.notConfigured();
    }

    @Override
    public SeasonForecastResponse forecastSeason(SeasonForecastRequest request) {
        throw AdvisoryUnavailable.notConfigured();
    }

    @Override
    public WeatherOutlookResponse weatherOutlook(WeatherOutlookRequest request) {
        throw AdvisoryUnavailable.notConfigured();
    }
}
