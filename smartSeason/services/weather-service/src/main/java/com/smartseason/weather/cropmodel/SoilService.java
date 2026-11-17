package com.smartseason.weather.cropmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Soil texture for a location from ISRIC SoilGrids (250 m, measured and modelled from soil
 * profiles worldwide). The public service goes down often, so every outcome is cached, a failure
 * for a short while and a success for a year, and a failure never fails the forecast: the model
 * falls back to an assumed loam and the response says the soil was assumed.
 */
@Service
public class SoilService {

    private static final Logger log = LoggerFactory.getLogger(SoilService.class);

    private record Cached(SoilProfile soil, Instant at, Duration ttl) {
        boolean fresh() {
            return at.plus(ttl).isAfter(Instant.now());
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public SoilService(@Value("${smartseason.climate.soil-url:https://rest.isric.org}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(12));
        this.client = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public SoilProfile resolve(double lat, double lon, String override) {
        Optional<SoilProfile> chosen = SoilProfile.fromName(override);
        if (chosen.isPresent()) {
            return chosen.get();
        }
        String key = String.format("%.3f,%.3f", Math.round(lat * 200) / 200.0, Math.round(lon * 200) / 200.0);
        Cached hit = cache.get(key);
        if (hit != null && hit.fresh()) {
            return hit.soil();
        }
        try {
            String raw = client.get().uri("/soilgrids/v2.0/properties/query?lon=" + lon + "&lat=" + lat
                    + "&property=clay&property=sand&depth=0-5cm&depth=5-15cm&depth=15-30cm"
                    + "&depth=30-60cm&depth=60-100cm&value=mean").retrieve().body(String.class);
            Optional<double[]> parsed = parse(mapper.readTree(raw));
            if (parsed.isPresent()) {
                SoilProfile soil = SoilProfile.fromClaySand(parsed.get()[0], parsed.get()[1]);
                cache.put(key, new Cached(soil, Instant.now(), Duration.ofDays(365)));
                return soil;
            }
            log.warn("SoilGrids answered but without clay and sand for {}", key);
        } catch (Exception ex) {
            log.warn("SoilGrids unavailable for {}: {}", key, ex.getMessage());
        }
        SoilProfile assumed = SoilProfile.assumed();
        cache.put(key, new Cached(assumed, Instant.now(), Duration.ofMinutes(30)));
        return assumed;
    }

    /**
     * Depth-weighted clay and sand percentages, {clay, sand}. SoilGrids reports grams per
     * kilogram with a divisor (10) that turns them into percent.
     */
    static Optional<double[]> parse(JsonNode root) {
        double clay = Double.NaN;
        double sand = Double.NaN;
        for (JsonNode layer : root.path("properties").path("layers")) {
            double divisor = layer.path("unit_measure").path("d_factor").asDouble(10);
            double weighted = 0;
            double thickness = 0;
            for (JsonNode depth : layer.path("depths")) {
                JsonNode mean = depth.path("values").path("mean");
                if (mean.isNull() || mean.isMissingNode()) {
                    continue;
                }
                double top = depth.path("range").path("top_depth").asDouble(0);
                double bottom = depth.path("range").path("bottom_depth").asDouble(top + 1);
                double t = Math.max(1, bottom - top);
                weighted += mean.asDouble() / divisor * t;
                thickness += t;
            }
            if (thickness == 0) {
                continue;
            }
            switch (layer.path("name").asText()) {
                case "clay" -> clay = weighted / thickness;
                case "sand" -> sand = weighted / thickness;
                default -> { }
            }
        }
        if (Double.isNaN(clay) || Double.isNaN(sand)) {
            return Optional.empty();
        }
        return Optional.of(new double[] {clay, sand});
    }
}
