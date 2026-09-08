package com.smartseason.agronomy.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.Candidate;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.ImagePayload;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.StageOutlook;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Agronomic advice from a Claude model, including photographs.
 *
 * Every answer is asked for as JSON and parsed strictly. If the call fails, the
 * response is malformed, or the model declines, the caller falls back to the
 * rules model rather than surfacing an error - a farmer standing in a field
 * with a diseased plant is better served by an honest rule of thumb than by a
 * stack trace.
 */
public class ClaudeAdvisoryModel implements AdvisoryModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAdvisoryModel.class);
    private static final int MAX_IMAGES = 4;

    private final RestClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;
    private final AdvisoryModel fallback;

    public ClaudeAdvisoryModel(RestClient client, String model, AdvisoryModel fallback) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public String source() {
        return "MODEL";
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an agronomist advising a Kenyan smallholder farmer.\n")
              .append("Crop: ").append(request.cropCode()).append('\n');
        if (request.growthStage() != null) {
            prompt.append("Growth stage: ").append(request.growthStage()).append('\n');
        }
        if (request.county() != null) {
            prompt.append("County: ").append(request.county()).append('\n');
        }
        if (request.symptoms() != null && !request.symptoms().isBlank()) {
            prompt.append("Reported symptoms: ").append(request.symptoms()).append('\n');
        }
        if (request.images() != null && !request.images().isEmpty()) {
            prompt.append("Photographs of the affected plants are attached. Examine them.\n");
        }
        prompt.append("""

                Identify the most likely causes. Reply with JSON only, no prose around it:
                {"candidates":[{"code":"","commonName":"","scientificName":"","confidence":0.0,
                "reasoning":""}],"severity":"INFO|LOW|MEDIUM|HIGH|CRITICAL","management":"",
                "immediateAction":"","caveats":[""]}

                Rules: at most three candidates, most likely first. confidence is 0-1 and must
                reflect real uncertainty - if the photograph is blurred or the symptoms are
                generic, say so in caveats and keep confidence low. Management advice must suit
                a smallholder: name the practice, not a brand. Never advise a pesticide dose.
                """);

        JsonNode json = ask(prompt.toString(), request.images());
        if (json == null) {
            return fallback.diagnose(request);
        }

        try {
            List<Candidate> candidates = new ArrayList<>();
            for (JsonNode node : json.path("candidates")) {
                candidates.add(new Candidate(
                        node.path("code").asText(null),
                        node.path("commonName").asText(null),
                        node.path("scientificName").asText(null),
                        node.path("confidence").asDouble(0),
                        node.path("reasoning").asText(null)));
            }
            List<String> caveats = new ArrayList<>();
            json.path("caveats").forEach(node -> caveats.add(node.asText()));

            return new DiagnosisResponse(
                    candidates,
                    json.path("severity").asText("MEDIUM"),
                    json.path("management").asText(""),
                    json.path("immediateAction").asText(""),
                    source(),
                    caveats);
        } catch (RuntimeException ex) {
            log.warn("Could not read the model's diagnosis; using rules instead", ex);
            return fallback.diagnose(request);
        }
    }

    @Override
    public SeasonForecastResponse forecastSeason(SeasonForecastRequest request) {
        String prompt = """
                You are an agronomist forecasting a Kenyan smallholder season.
                Crop: %s (%s), planted %s on %s ha in %s.
                Current stage: %s. Rain in the last 7 days: %s mm. Average temperature: %s C.
                The farmer expects %s kg.

                Reply with JSON only:
                {"expectedHarvestDate":"YYYY-MM-DD","projectedYieldKg":0,"confidence":0.0,
                "stages":[{"stage":"","expectedStart":"YYYY-MM-DD","expectedEnd":"YYYY-MM-DD",
                "guidance":""}],"risks":[""],"summary":""}

                Base the yield on typical rainfed smallholder performance for this crop in Kenya,
                adjusted for the weather given. Keep confidence honest - you have a handful of
                figures, not a field trial.
                """.formatted(
                request.cropCode(), nullSafe(request.variety()), request.startDate(),
                nullSafe(request.areaHa()), nullSafe(request.county()),
                nullSafe(request.currentStage()), nullSafe(request.recentRainfallMm()),
                nullSafe(request.averageTempC()), nullSafe(request.expectedYieldKg()));

        JsonNode json = ask(prompt, null);
        if (json == null) {
            return fallback.forecastSeason(request);
        }

        try {
            List<StageOutlook> stages = new ArrayList<>();
            for (JsonNode node : json.path("stages")) {
                stages.add(new StageOutlook(
                        node.path("stage").asText(),
                        LocalDate.parse(node.path("expectedStart").asText()),
                        LocalDate.parse(node.path("expectedEnd").asText()),
                        node.path("guidance").asText()));
            }
            List<String> risks = new ArrayList<>();
            json.path("risks").forEach(node -> risks.add(node.asText()));

            return new SeasonForecastResponse(
                    LocalDate.parse(json.path("expectedHarvestDate").asText()),
                    json.path("projectedYieldKg").asDouble(),
                    json.path("confidence").asDouble(),
                    stages, risks, json.path("summary").asText(""), source());
        } catch (RuntimeException ex) {
            log.warn("Could not read the model's forecast; using rules instead", ex);
            return fallback.forecastSeason(request);
        }
    }

    @Override
    public WeatherOutlookResponse weatherOutlook(WeatherOutlookRequest request) {
        String prompt = """
                You advise Kenyan smallholders on field operations.
                County: %s. Crop: %s at stage %s.
                Rain in the last 7 days: %s mm. Average temperature: %s C. Soil moisture: %s%%.

                Reply with JSON only:
                {"outlook":"Wet|Dry|Mixed","advisories":[""],"irrigationAdvice":"","sprayWindow":""}

                Be specific and practical. Say when to act, not just what.
                """.formatted(
                request.county(), nullSafe(request.cropCode()), nullSafe(request.growthStage()),
                nullSafe(request.rainfallLast7DaysMm()), nullSafe(request.averageTempC()),
                nullSafe(request.soilMoisturePct()));

        JsonNode json = ask(prompt, null);
        if (json == null) {
            return fallback.weatherOutlook(request);
        }

        try {
            List<String> advisories = new ArrayList<>();
            json.path("advisories").forEach(node -> advisories.add(node.asText()));
            return new WeatherOutlookResponse(
                    json.path("outlook").asText("Mixed"), advisories,
                    json.path("irrigationAdvice").asText(""),
                    json.path("sprayWindow").asText(""), source());
        } catch (RuntimeException ex) {
            log.warn("Could not read the model's outlook; using rules instead", ex);
            return fallback.weatherOutlook(request);
        }
    }

    /** Sends one message and returns the parsed JSON object, or null on any failure. */
    private JsonNode ask(String prompt, List<ImagePayload> images) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 1500);

            ArrayNode content = mapper.createArrayNode();
            if (images != null) {
                int attached = 0;
                for (ImagePayload image : images) {
                    if (attached++ >= MAX_IMAGES) {
                        break;
                    }
                    ObjectNode block = content.addObject();
                    block.put("type", "image");
                    ObjectNode sourceNode = block.putObject("source");
                    sourceNode.put("type", "base64");
                    sourceNode.put("media_type", image.mediaType());
                    sourceNode.put("data", image.base64());
                }
            }
            content.addObject().put("type", "text").put("text", prompt);

            ArrayNode messages = body.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.set("content", content);

            String raw = client.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode response = mapper.readTree(raw);
            StringBuilder text = new StringBuilder();
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return mapper.readTree(extractJson(text.toString()));
        } catch (Exception ex) {
            log.warn("The advisory model did not answer; falling back to rules: {}", ex.getMessage());
            return null;
        }
    }

    /** Models sometimes wrap JSON in prose or a fence; take the outermost object. */
    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private static String nullSafe(Object value) {
        return value == null ? "unknown" : value.toString();
    }
}
