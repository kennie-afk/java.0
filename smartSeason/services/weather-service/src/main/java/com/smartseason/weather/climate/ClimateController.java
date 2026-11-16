package com.smartseason.weather.climate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/weather/v1/climate")
@Tag(name = "Climate", description = "Real rainfall outlook and planting advice for a location")
public class ClimateController {

    private final ClimateService service;

    public ClimateController(ClimateService service) {
        this.service = service;
    }

    @GetMapping("/planting")
    @Operation(summary = "Expected rainfall and what to plant, from forecast, seasonal model and history")
    public PlantingAdvisor.Result planting(@RequestParam double lat, @RequestParam double lon) {
        // Kenya sits inside roughly 5S-5N and 33E-42E; leave a margin rather than reject borders.
        if (lat < -6 || lat > 6 || lon < 32 || lon > 43) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Location is outside the area this advice is calibrated for (Kenya and neighbours).");
        }
        return service.planting(lat, lon);
    }
}
