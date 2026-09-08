package com.smartseason.agronomy.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.ImagePayload;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeuristicAdvisoryModelTest {

    private final HeuristicAdvisoryModel model = new HeuristicAdvisoryModel();

    @Test
    void namesFallArmywormFromItsCharacteristicSigns() {
        DiagnosisResponse response = model.diagnose(new DiagnosisRequest(
                "MAIZE", "ragged holes in the whorl with frass and window-paning on young leaves",
                null, "Nakuru", "Vegetative"));

        assertThat(response.candidates()).isNotEmpty();
        assertThat(response.candidates().getFirst().code()).isEqualTo("FAW");
        assertThat(response.source()).isEqualTo("RULES");
    }

    @Test
    void separatesLateBlightFromFallArmywormByCrop() {
        DiagnosisResponse response = model.diagnose(new DiagnosisRequest(
                "POTATO", "water-soaked lesions spreading after rain, white mould underneath",
                null, "Nyandarua", "Tuber bulking"));

        assertThat(response.candidates().getFirst().code()).isEqualTo("LATE_BLIGHT");
    }

    @Test
    void saysPlainlyWhenItCannotTell() {
        DiagnosisResponse response = model.diagnose(new DiagnosisRequest(
                "MAIZE", "the crop looks a bit unusual", null, null, null));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.caveats()).anyMatch(c -> c.toLowerCase().contains("agronomist"));
    }

    @Test
    void admitsThatItCannotLookAtPhotographs() {
        DiagnosisResponse response = model.diagnose(new DiagnosisRequest(
                "MAIZE", "holes in the whorl",
                List.of(new ImagePayload("image/jpeg", "AAAA")), null, null));

        assertThat(response.caveats()).anyMatch(c -> c.contains("Photographs were not examined"));
    }

    @Test
    void confidenceNeverReachesCertainty() {
        DiagnosisResponse response = model.diagnose(new DiagnosisRequest(
                "MAIZE", "window whorl frass ragged holes caterpillar worm", null, null, null));

        assertThat(response.candidates().getFirst().confidence()).isLessThanOrEqualTo(0.75);
    }

    @Test
    void projectsHarvestAndYieldFromPlantingDateAndArea() {
        SeasonForecastResponse response = model.forecastSeason(new SeasonForecastRequest(
                "MAIZE", "H614D", "2026-03-15", 6.0, "Nakuru", 16200.0, "TASSELING", 60.0, 21.0));

        // Maize is taken as 150 days to maturity.
        assertThat(response.expectedHarvestDate()).isEqualTo("2026-08-12");
        assertThat(response.projectedYieldKg()).isEqualTo(2700 * 6.0);
        assertThat(response.stages()).hasSize(5);
        assertThat(response.source()).isEqualTo("RULES");
    }

    @Test
    void cutsTheProjectionWhenTheWeatherIsAgainstTheCrop() {
        SeasonForecastRequest dry = new SeasonForecastRequest(
                "MAIZE", null, "2026-03-15", 1.0, "Nakuru", null, null, 5.0, 33.0);

        SeasonForecastResponse response = model.forecastSeason(dry);

        assertThat(response.projectedYieldKg()).isLessThan(2700);
        assertThat(response.risks()).hasSizeGreaterThan(1);
    }

    @Test
    void callsForIrrigationWhenSoilMoistureIsLow() {
        var response = model.weatherOutlook(new WeatherOutlookRequest(
                "Nakuru", "MAIZE", "Tasseling", 8.0, 26.0, 18.0));

        assertThat(response.irrigationAdvice()).contains("48 hours");
        assertThat(response.outlook()).isEqualTo("Dry");
    }

    @Test
    void warnsAgainstSprayingIntoRain() {
        var response = model.weatherOutlook(new WeatherOutlookRequest(
                "Nyandarua", "POTATO", "Tuber bulking", 140.0, 18.0, 60.0));

        assertThat(response.outlook()).isEqualTo("Wet");
        assertThat(response.sprayWindow()).contains("24 hours");
    }
}
