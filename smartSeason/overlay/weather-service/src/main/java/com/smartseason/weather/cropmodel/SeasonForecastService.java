package com.smartseason.weather.cropmodel;

import com.smartseason.weather.cropmodel.SeasonForecaster.Forecast;
import com.smartseason.weather.cropmodel.SeasonForecaster.Inputs;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Gathers the real inputs for a location and runs the crop model over them. */
@Service
public class SeasonForecastService {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    /** Where every number came from, and how fresh it is. */
    public record Basis(Instant generatedAt, Instant forecastFetchedAt, Instant seasonalFetchedAt,
                        int seasonalMembers, int historyYears, String soilSource, String soilTexture,
                        String method, String maturity, Double attainableKgPerHa, boolean attainableFromCaller) {
    }

    public record Response(Forecast forecast, Basis basis, List<String> caveats) {
    }

    private final WeatherFeed feed;
    private final SoilService soils;

    public SeasonForecastService(WeatherFeed feed, SoilService soils) {
        this.feed = feed;
        this.soils = soils;
    }

    public Response forecast(double lat, double lon, String cropCode, LocalDate plantingDate,
                             String soilOverride, Double attainableOverride,
                             String maturity) {
        double factor = CropParams.maturityFactor(maturity).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "maturity must be EARLY, MEDIUM or LATE."));
        CropParams crop = CropParams.find(cropCode).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Unsupported crop. Supported: "
                + CropParams.ALL.stream().map(CropParams::code).toList()))
                .withMaturityFactor(factor);
        double rlat = WeatherFeed.snap(lat);
        double rlon = WeatherFeed.snap(lon);
        LocalDate today = LocalDate.now(NAIROBI);
        LocalDate planting = plantingDate == null ? today : plantingDate;

        var near = feed.near(rlat, rlon);
        var members = feed.seasonal(rlat, rlon);
        var history = feed.history(rlat, rlon, today);
        SoilProfile soil = soils.resolve(lat, lon, soilOverride);
        double attainable = attainableOverride != null ? attainableOverride : crop.attainableKgPerHa();

        Forecast f;
        try {
            f = SeasonForecaster.forecast(crop, soil,
                    new Inputs(today, rlat, near.value(), members.value(), history.value()),
                    planting, attainable);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("Models water and heat only. Nutrients, pests, disease, frost, hail, waterlogging, "
                + "runoff on slopes and the variety are not in it, so treat the yield as what the "
                + "weather and soil allow, not a promise.");
        if (soil.source() == SoilProfile.Source.ASSUMED) {
            caveats.add("Soil data was unavailable, so a loam soil was assumed. Pass soil=SAND, SANDY_LOAM, "
                    + "LOAM, CLAY_LOAM or CLAY if you know it: soil changes how well a crop rides out dry spells.");
        }
        if (members.value().isEmpty()) {
            caveats.add("The seasonal weather model did not answer, so the spread comes from past years only "
                    + "(plus the 16-day forecast).");
        } else if (f.seasonCoveredByModel() < 0.5) {
            caveats.add("Most of this season lies beyond the seasonal model's reach; the spread mostly "
                    + "reflects what past years did on the same dates.");
        }
        if (attainableOverride == null) {
            caveats.add("Kilograms use a typical attainable yield for " + crop.name() + " ("
                    + Math.round(attainable) + " kg/ha). The percentage is the more reliable figure; "
                    + "pass attainableKgPerHa to use your own best harvest.");
        }
        if (maturity == null || maturity.isBlank()) {
            caveats.add("Variety maturity was not given, so a medium-maturing variety is assumed. Pass "
                    + "maturity=EARLY or LATE if you know it: it moves harvest dates by weeks.");
        }
        caveats.add("Soil moisture at the start is assumed half full and corrects itself from the last "
                + "week's rain; stage lengths use typical heat requirements that vary by variety.");

        Basis basis = new Basis(Instant.now(), near.fetchedAt(), members.fetchedAt(),
                members.value().size(), 25, soil.source().name(), soil.texture(),
                "FAO-56 soil-water balance with heat-driven stages and FAO-33 yield response, run over "
                        + "seasonal-model members and historical years",
                maturity == null || maturity.isBlank() ? "MEDIUM" : maturity.trim().toUpperCase(),
                attainable, attainableOverride != null);
        return new Response(f, basis, caveats);
    }
}
