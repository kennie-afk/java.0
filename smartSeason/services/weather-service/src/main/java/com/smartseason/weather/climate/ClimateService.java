package com.smartseason.weather.climate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fetches real rainfall data from Open-Meteo (free, no key) and feeds the advisor.
 *
 * Three calls per location: the 16-day forecast, the seasonal ensemble, and twenty years
 * of daily history. Locations are rounded to 0.1 degree (about 11 km), which is the grid
 * the data comes on anyway, and cached: forecasts for a few hours, history for a month.
 */
@Service
public class ClimateService {

    private static final Logger log = LoggerFactory.getLogger(ClimateService.class);
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private record Cached<T>(T value, Instant at) {
        boolean fresh(Duration ttl) {
            return at.plus(ttl).isAfter(Instant.now());
        }
    }

    private record Forecast(double[] past7, double[] next16) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient forecastClient;
    private final RestClient seasonalClient;
    private final RestClient archiveClient;
    private final Map<String, Cached<Forecast>> forecasts = new ConcurrentHashMap<>();
    private final Map<String, Cached<List<double[]>>> seasonal = new ConcurrentHashMap<>();
    private final Map<String, Cached<Map<LocalDate, Double>>> history = new ConcurrentHashMap<>();

    public ClimateService(
            @Value("${smartseason.climate.forecast-url:https://api.open-meteo.com}") String forecastUrl,
            @Value("${smartseason.climate.seasonal-url:https://seasonal-api.open-meteo.com}") String seasonalUrl,
            @Value("${smartseason.climate.archive-url:https://archive-api.open-meteo.com}") String archiveUrl) {
        this.forecastClient = client(forecastUrl);
        this.seasonalClient = client(seasonalUrl);
        this.archiveClient = client(archiveUrl);
    }

    private static RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(40));
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public PlantingAdvisor.Result planting(double lat, double lon) {
        double rlat = Math.round(lat * 10) / 10.0;
        double rlon = Math.round(lon * 10) / 10.0;
        String key = rlat + "," + rlon;
        LocalDate today = LocalDate.now(NAIROBI);

        Forecast forecast = forecast(key, rlat, rlon);
        List<double[]> members = seasonal(key, rlat, rlon);
        Map<LocalDate, Double> past = history(key, rlat, rlon, today);
        return PlantingAdvisor.advise(today, forecast.past7(), forecast.next16(), members, past);
    }

    private Forecast forecast(String key, double lat, double lon) {
        Cached<Forecast> hit = forecasts.get(key);
        if (hit != null && hit.fresh(Duration.ofHours(3))) {
            return hit.value();
        }
        try {
            JsonNode daily = get(forecastClient, "/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&daily=precipitation_sum&past_days=7&forecast_days=16&timezone=Africa/Nairobi")
                    .path("daily").path("precipitation_sum");
            double[] all = new double[daily.size()];
            for (int i = 0; i < all.length; i++) {
                all[i] = daily.get(i).asDouble(0);
            }
            int past = Math.min(7, all.length);
            Forecast f = new Forecast(java.util.Arrays.copyOfRange(all, 0, past),
                    java.util.Arrays.copyOfRange(all, past, all.length));
            if (f.next16().length == 0) {
                throw new IllegalStateException("empty forecast");
            }
            forecasts.put(key, new Cached<>(f, Instant.now()));
            return f;
        } catch (RuntimeException ex) {
            if (hit != null) {
                return hit.value();
            }
            throw unavailable("weather forecast", ex);
        }
    }

    /** The seasonal ensemble is the one input the advice can survive without. */
    private List<double[]> seasonal(String key, double lat, double lon) {
        Cached<List<double[]>> hit = seasonal.get(key);
        if (hit != null && hit.fresh(Duration.ofHours(12))) {
            return hit.value();
        }
        try {
            JsonNode daily = get(seasonalClient, "/v1/seasonal?latitude=" + lat + "&longitude=" + lon
                    + "&daily=precipitation_sum&forecast_days=217&timezone=Africa/Nairobi").path("daily");
            List<double[]> members = new ArrayList<>();
            daily.fieldNames().forEachRemaining(name -> {
                if (name.startsWith("precipitation_sum_member")) {
                    JsonNode series = daily.path(name);
                    double[] values = new double[series.size()];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = series.get(i).asDouble(0);
                    }
                    members.add(values);
                }
            });
            seasonal.put(key, new Cached<>(members, Instant.now()));
            return members;
        } catch (RuntimeException ex) {
            log.warn("Seasonal ensemble unavailable for {}: {}", key, ex.getMessage());
            return hit != null ? hit.value() : List.of();
        }
    }

    private Map<LocalDate, Double> history(String key, double lat, double lon, LocalDate today) {
        Cached<Map<LocalDate, Double>> hit = history.get(key);
        if (hit != null && hit.fresh(Duration.ofDays(30))) {
            return hit.value();
        }
        try {
            int endYear = today.getYear() - 1;
            JsonNode daily = get(archiveClient, "/v1/archive?latitude=" + lat + "&longitude=" + lon
                    + "&start_date=" + (endYear - 24) + "-01-01&end_date=" + endYear
                    + "-12-31&daily=precipitation_sum&timezone=Africa/Nairobi").path("daily");
            JsonNode times = daily.path("time");
            JsonNode rain = daily.path("precipitation_sum");
            Map<LocalDate, Double> map = new HashMap<>(times.size() * 2);
            for (int i = 0; i < times.size(); i++) {
                if (!rain.get(i).isNull()) {
                    map.put(LocalDate.parse(times.get(i).asText()), rain.get(i).asDouble());
                }
            }
            if (map.isEmpty()) {
                throw new IllegalStateException("empty history");
            }
            history.put(key, new Cached<>(map, Instant.now()));
            return map;
        } catch (RuntimeException ex) {
            if (hit != null) {
                return hit.value();
            }
            throw unavailable("rainfall history", ex);
        }
    }

    private JsonNode get(RestClient client, String uri) {
        try {
            return mapper.readTree(client.get().uri(uri).retrieve().body(String.class));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("unreadable response", ex);
        }
    }

    private static ResponseStatusException unavailable(String what, RuntimeException cause) {
        log.warn("Could not fetch {}: {}", what, cause.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The " + what + " service did not answer. Try again in a few minutes.");
    }
}
