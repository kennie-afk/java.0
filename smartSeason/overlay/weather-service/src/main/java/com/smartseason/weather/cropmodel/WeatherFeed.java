package com.smartseason.weather.cropmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.weather.cropmodel.SeasonForecaster.Member;
import com.smartseason.weather.cropmodel.SeasonForecaster.Near;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * Rain, temperature and evaporation for one location from Open-Meteo: the 16-day forecast, the
 * seasonal ensemble and 25 years of daily history. Locations are rounded to 0.1 degree, the grid
 * the data comes on. Each source is cached; if a refresh fails the last good copy is served, and
 * only the seasonal model may be missing altogether, in which case the forecast rests on
 * history and says so.
 */
@Service
public class WeatherFeed {

    private static final Logger log = LoggerFactory.getLogger(WeatherFeed.class);
    private static final String ZONE = "Africa/Nairobi";

    private record Cached<T>(T value, Instant at) {
        boolean fresh(Duration ttl) {
            return at.plus(ttl).isAfter(Instant.now());
        }
    }

    /** What a forecast was built from, for the response's provenance. */
    public record Fetched<T>(T value, Instant fetchedAt) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient forecastClient;
    private final RestClient seasonalClient;
    private final RestClient archiveClient;
    private final Map<String, Cached<Near>> near = new ConcurrentHashMap<>();
    private final Map<String, Cached<List<Member>>> seasonal = new ConcurrentHashMap<>();
    private final Map<String, Cached<History>> history = new ConcurrentHashMap<>();

    public WeatherFeed(
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
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }

    public static double snap(double degrees) {
        return Math.round(degrees * 10) / 10.0;
    }

    public Fetched<Near> near(double lat, double lon) {
        String key = lat + "," + lon;
        Cached<Near> hit = near.get(key);
        if (hit != null && hit.fresh(Duration.ofHours(3))) {
            return new Fetched<>(hit.value(), hit.at());
        }
        try {
            JsonNode daily = get(forecastClient, "/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&daily=precipitation_sum,et0_fao_evapotranspiration,temperature_2m_max,temperature_2m_min"
                    + "&past_days=" + SeasonForecaster.PAST_DAYS + "&forecast_days=16&timezone=" + ZONE)
                    .path("daily");
            Near n = new Near(series(daily.path("precipitation_sum")),
                    series(daily.path("et0_fao_evapotranspiration")),
                    series(daily.path("temperature_2m_max")), series(daily.path("temperature_2m_min")));
            if (n.rain().length < SeasonForecaster.PAST_DAYS + 7) {
                throw new IllegalStateException("short forecast");
            }
            near.put(key, new Cached<>(n, Instant.now()));
            return new Fetched<>(n, Instant.now());
        } catch (RuntimeException ex) {
            if (hit != null) {
                return new Fetched<>(hit.value(), hit.at());
            }
            throw unavailable("weather forecast", ex);
        }
    }

    /** Empty when the seasonal model is down; the forecast then rests on history. */
    public Fetched<List<Member>> seasonal(double lat, double lon) {
        String key = lat + "," + lon;
        Cached<List<Member>> hit = seasonal.get(key);
        if (hit != null && hit.fresh(Duration.ofHours(12))) {
            return new Fetched<>(hit.value(), hit.at());
        }
        try {
            JsonNode daily = get(seasonalClient, "/v1/seasonal?latitude=" + lat + "&longitude=" + lon
                    + "&daily=precipitation_sum,temperature_2m_max,temperature_2m_min"
                    + "&forecast_days=217&timezone=" + ZONE).path("daily");
            Map<String, double[]> rain = new TreeMap<>();
            Map<String, double[]> tmax = new TreeMap<>();
            Map<String, double[]> tmin = new TreeMap<>();
            daily.fieldNames().forEachRemaining(name -> {
                int at = name.indexOf("_member");
                if (at < 0) {
                    return;
                }
                String variable = name.substring(0, at);
                String member = name.substring(at + 7);
                double[] values = series(daily.path(name));
                switch (variable) {
                    case "precipitation_sum" -> rain.put(member, values);
                    case "temperature_2m_max" -> tmax.put(member, values);
                    case "temperature_2m_min" -> tmin.put(member, values);
                    default -> { }
                }
            });
            List<Member> members = new ArrayList<>();
            for (String id : rain.keySet()) {
                if (tmax.containsKey(id) && tmin.containsKey(id)) {
                    members.add(new Member(rain.get(id), tmax.get(id), tmin.get(id)));
                }
            }
            seasonal.put(key, new Cached<>(members, Instant.now()));
            return new Fetched<>(members, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Seasonal ensemble unavailable for {}: {}", key, ex.getMessage());
            return hit != null ? new Fetched<>(hit.value(), hit.at())
                    : new Fetched<>(List.of(), Instant.now());
        }
    }

    public Fetched<History> history(double lat, double lon, LocalDate today) {
        String key = lat + "," + lon;
        Cached<History> hit = history.get(key);
        if (hit != null && hit.fresh(Duration.ofDays(30))) {
            return new Fetched<>(hit.value(), hit.at());
        }
        try {
            int endYear = today.getYear() - 1;
            LocalDate start = LocalDate.of(endYear - 24, 1, 1);
            JsonNode daily = get(archiveClient, "/v1/archive?latitude=" + lat + "&longitude=" + lon
                    + "&start_date=" + start + "&end_date=" + endYear
                    + "-12-31&daily=precipitation_sum,et0_fao_evapotranspiration,temperature_2m_max,"
                    + "temperature_2m_min&timezone=" + ZONE).path("daily");
            double[] rain = nullable(daily.path("precipitation_sum"));
            if (rain.length == 0) {
                throw new IllegalStateException("empty history");
            }
            History h = new History(start, rain, nullable(daily.path("et0_fao_evapotranspiration")),
                    nullable(daily.path("temperature_2m_max")), nullable(daily.path("temperature_2m_min")));
            history.put(key, new Cached<>(h, Instant.now()));
            return new Fetched<>(h, Instant.now());
        } catch (RuntimeException ex) {
            if (hit != null) {
                return new Fetched<>(hit.value(), hit.at());
            }
            throw unavailable("rainfall history", ex);
        }
    }

    /** Forecast series: a missing value repeats the previous one rather than becoming zero. */
    private static double[] series(JsonNode node) {
        double[] out = new double[node.size()];
        double last = 0;
        for (int i = 0; i < out.length; i++) {
            if (!node.get(i).isNull()) {
                last = node.get(i).asDouble();
            }
            out[i] = last;
        }
        return out;
    }

    /** History series: a missing value stays NaN and is filled with the calendar-day average. */
    private static double[] nullable(JsonNode node) {
        double[] out = new double[node.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = node.get(i).isNull() ? Double.NaN : node.get(i).asDouble();
        }
        return out;
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
