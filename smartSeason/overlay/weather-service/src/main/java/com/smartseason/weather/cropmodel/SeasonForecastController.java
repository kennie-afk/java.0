package com.smartseason.weather.cropmodel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/weather/v1/climate")
@Tag(name = "Climate", description = "Crop-model season forecast from real weather and soil data")
public class SeasonForecastController {

    private final SeasonForecastService service;

    public SeasonForecastController(SeasonForecastService service) {
        this.service = service;
    }

    @GetMapping("/season-forecast")
    @Operation(summary = "Yield range, harvest dates, stage dates and the best planting dates for a crop "
            + "at a location, from a soil-water balance run over real forecasts")
    public SeasonForecastService.Response forecast(
            @RequestParam double lat, @RequestParam double lon, @RequestParam String crop,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plantingDate,
            @RequestParam(required = false) String soil,
            @RequestParam(required = false) Double attainableKgPerHa,
            @RequestParam(required = false) String maturity) {
        if (lat < -6 || lat > 6 || lon < 32 || lon > 43) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Location is outside the area this advice is calibrated for (Kenya and neighbours).");
        }
        if (attainableKgPerHa != null && (attainableKgPerHa < 100 || attainableKgPerHa > 100_000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "attainableKgPerHa must be between 100 and 100000.");
        }
        return service.forecast(lat, lon, crop, plantingDate, soil, attainableKgPerHa, maturity);
    }
}
