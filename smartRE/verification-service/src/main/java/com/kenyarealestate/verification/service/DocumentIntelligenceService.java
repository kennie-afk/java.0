package com.kenyarealestate.verification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

@Slf4j
@Service
public class DocumentIntelligenceService {

    private RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutorService geminiExecutor;
    private ExecutorService fanOutExecutor;

    @Value("${services.gemini-connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${services.gemini-read-timeout-ms:25000}")
    private int readTimeoutMs;

    @Value("${services.gemini-hard-timeout-ms:30000}")
    private int hardTimeoutMs;

    @PostConstruct
    private void initRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        restTemplate = new RestTemplate(factory);
        geminiExecutor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "gemini-call");
            t.setDaemon(true);
            return t;
        });
        fanOutExecutor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "gemini-fanout");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    private void shutdownExecutor() {
        if (geminiExecutor != null) geminiExecutor.shutdownNow();
        if (fanOutExecutor != null) fanOutExecutor.shutdownNow();
    }

    @Value("${services.document-analysis-enabled}")
    private boolean analysisEnabled;

    public boolean isEnabled() {
        return analysisEnabled;
    }

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @Value("${services.document-url-allowed-hosts:localhost,user-service}")
    private String documentUrlAllowedHostsCsv;

    @Value("${services.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${services.gemini-model:gemini-3.6-flash}")
    private String geminiModel;

    @Value("${services.gemini-base-url:https://generativelanguage.googleapis.com/v1beta/models/}")
    private String geminiBaseUrl;

    private record CategoryHint(String description, List<String> extractableFields) {
        CategoryHint(String description) {
            this(description, List.of());
        }
    }

    private static final Set<String> ID_NUMBER_BEARING_CATEGORIES = Set.of(
            "NATIONAL_ID_FRONT", "NATIONAL_ID_BACK", "PASSPORT", "KRA_PIN_CERTIFICATE");

    private static final Map<String, CategoryHint> IDENTITY_CATEGORY_HINTS = Map.ofEntries(
            Map.entry("NATIONAL_ID_FRONT", new CategoryHint(
                    "Front of a Kenyan National ID card: holder's photo, full names, ID number (7-8 digits), date of birth, sex, district of birth, hologram.")),
            Map.entry("NATIONAL_ID_BACK", new CategoryHint(
                    "Back of a Kenyan National ID card: holder's signature, serial number, machine-readable/barcode zone. No large front-facing photo of the holder.")),
            Map.entry("KRA_PIN_CERTIFICATE", new CategoryHint(
                    "Kenya Revenue Authority PIN certificate: KRA letterhead, PIN starting with A or P, taxpayer name, registration date.")),
            Map.entry("PASSPORT", new CategoryHint(
                    "Kenyan passport bio-data page: photo, passport number, full names, nationality, date of birth.")),
            Map.entry("BUSINESS_REGISTRATION_CERTIFICATE", new CategoryHint(
                    "Certificate of business name or company registration from the Registrar of Companies / Business Registration Service.")),
            Map.entry("SELFIE_WITH_ID", new CategoryHint(
                    "A selfie photo of a person holding a physical ID card next to their own face — both the face and the ID must be clearly visible in the same shot.")),
            Map.entry("UTILITY_BILL", new CategoryHint(
                    "A utility bill (electricity, water, etc.) showing a customer name and service address.")),
            Map.entry("BANK_STATEMENT", new CategoryHint(
                    "A bank account statement showing the account holder's name and transaction history."))
    );

    private static final Map<String, CategoryHint> OWNERSHIP_CATEGORY_HINTS = Map.ofEntries(
            Map.entry("TITLE_DEED", new CategoryHint(
                    "A Kenyan land title deed: registered proprietor's name, parcel/title number, land reference number, registration date, land size.",
                    List.of("ownerName", "parcelNumber", "titleNumber", "registrationDate", "landSize"))),
            Map.entry("LAND_SEARCH_CERTIFICATE", new CategoryHint(
                    "An official land registry search certificate confirming the registered proprietor of a parcel.",
                    List.of("parcelNumber", "proprietorName", "searchDate"))),
            Map.entry("LAND_RENT_CLEARANCE", new CategoryHint(
                    "A land rent clearance certificate from the Ministry of Lands confirming no outstanding land rent.",
                    List.of("parcelNumber", "clearanceDate"))),
            Map.entry("RATES_CLEARANCE_CERTIFICATE", new CategoryHint(
                    "A county rates clearance certificate confirming no outstanding land rates.",
                    List.of("parcelOrPlotNumber", "clearanceDate"))),
            Map.entry("CONSENT_TO_TRANSFER", new CategoryHint(
                    "A consent-to-transfer form/letter authorizing transfer of land, typically from a Land Control Board or Commissioner of Lands.",
                    List.of("grantorName", "granteeName", "parcelNumber", "consentDate"))),
            Map.entry("LAND_CONTROL_BOARD_CONSENT", new CategoryHint(
                    "Minutes or consent from a Land Control Board meeting approving a land transaction.",
                    List.of("parcelNumber", "applicantNames", "meetingDate"))),
            Map.entry("TRANSFER_FORM_RL1", new CategoryHint(
                    "Kenyan Land Registration Form RL1 (transfer of land): transferor and transferee, parcel number, consideration amount.",
                    List.of("transferorName", "transfereeName", "parcelNumber", "considerationAmount", "date"))),
            Map.entry("SURVEY_MAP", new CategoryHint(
                    "A registered land survey plan/map showing parcel boundaries.",
                    List.of("parcelNumber", "surveyPlanNumber", "surveyDate"))),
            Map.entry("SPOUSAL_CONSENT", new CategoryHint(
                    "A spousal consent letter or affidavit for a land transaction.",
                    List.of("spouseName", "ownerName", "parcelNumber", "date"))),
            Map.entry("MUTATION_FORM", new CategoryHint(
                    "A land mutation form recording subdivision or amalgamation of a parcel.",
                    List.of("originalParcelNumber", "newParcelNumbers", "date"))),
            Map.entry("LEASE_AGREEMENT", new CategoryHint(
                    "A lease agreement for land or property.",
                    List.of("lessorName", "lesseeName", "parcelNumber", "leaseTermYears", "date"))),
            Map.entry("LANDLORD_AUTHORIZATION", new CategoryHint(
                    "A letter from a landlord authorizing another party to act on a property.",
                    List.of("landlordName", "authorizedPartyName", "propertyAddress", "date"))),
            Map.entry("PROBATE_GRANT", new CategoryHint(
                    "A grant of probate or letters of administration from a Kenyan court.",
                    List.of("deceasedName", "executorName", "courtName", "grantDate"))),
            Map.entry("POWER_OF_ATTORNEY", new CategoryHint(
                    "A power of attorney document.",
                    List.of("donorName", "attorneyName", "scope", "date"))),
            Map.entry("SERVICE_CHARGE_CLEARANCE", new CategoryHint(
                    "A service charge clearance certificate (e.g. from an apartment/estate management company).",
                    List.of("unitNumber", "clearanceDate"))),
            Map.entry("BUSINESS_PERMIT", new CategoryHint(
                    "A county business permit.",
                    List.of("businessName", "permitNumber", "issuingCounty", "expiryDate")))
    );

    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("^\\d{7,8}$");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Z][0-9]{6,8}$");
    private static final Pattern KRA_PIN_PATTERN = Pattern.compile("^[AP]\\d{9}[A-Z]$");

    private boolean isValidIdNumberForCategory(String category, String candidate) {
        return switch (category) {
            case "NATIONAL_ID_FRONT", "NATIONAL_ID_BACK" -> NATIONAL_ID_PATTERN.matcher(candidate).matches();
            case "PASSPORT" -> PASSPORT_PATTERN.matcher(candidate).matches();
            case "KRA_PIN_CERTIFICATE" -> KRA_PIN_PATTERN.matcher(candidate).matches();
            default -> false;
        };
    }

    public String sha256Hex(String text) {
        if (!StringUtils.hasText(text)) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.trim().toUpperCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            log.error("Failed to hash identifier: {}", e.getMessage());
            return null;
        }
    }

    public String computeSha256FromUrl(String documentUrl) {
        try {
            assertUrlIsSafeToFetch(documentUrl);
            String fetchUrl = resolveFetchUrl(documentUrl);
            URI uri = URI.create(fetchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            if (fetchUrl.contains("/api/documents/internal/files/")) {
                conn.setRequestProperty("X-Internal-Secret", internalSecret);
            }
            try (InputStream in = conn.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
                return HexFormat.of().formatHex(md.digest());
            }
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 for document URL {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private String resolveFetchUrl(String documentUrl) {
        int idx = documentUrl.indexOf("/api/documents/files/");
        if (idx == -1) return documentUrl;
        String suffix = documentUrl.substring(idx + "/api/documents/".length());
        return userServiceUrl + "/api/documents/internal/" + suffix;
    }

    private void assertUrlIsSafeToFetch(String documentUrl) {
        if (!StringUtils.hasText(documentUrl)) {
            throw new IllegalArgumentException("Document URL is blank");
        }
        if (documentUrl.contains("/api/documents/files/")) {
            return;
        }
        URI uri = URI.create(documentUrl);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Document URL must use http or https");
        }
        String host = uri.getHost();
        if (host == null || !allowedHosts().contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Document URL host '" + host + "' is not an approved document storage host");
        }
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(documentUrlAllowedHostsCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private byte[] fetchRawBytes(String documentUrl) throws Exception {
        assertUrlIsSafeToFetch(documentUrl);
        String fetchUrl = resolveFetchUrl(documentUrl);
        URI uri = URI.create(fetchUrl);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        if (fetchUrl.contains("/api/documents/internal/files/")) {
            conn.setRequestProperty("X-Internal-Secret", internalSecret);
        }
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private boolean isPdf(String mimeType, byte[] rawBytes) {
        boolean claimsPdf = mimeType != null && mimeType.equalsIgnoreCase("application/pdf");
        boolean looksLikePdf = rawBytes.length >= 4
                && rawBytes[0] == '%' && rawBytes[1] == 'P' && rawBytes[2] == 'D' && rawBytes[3] == 'F';
        if (claimsPdf != looksLikePdf) {
            log.warn("mimeType/magic-byte mismatch for document (claims PDF: {}, looks like PDF: {}) - trusting magic bytes",
                    claimsPdf, looksLikePdf);
        }
        return looksLikePdf;
    }

    private byte[] rasterizeFirstPdfPageToPng(byte[] pdfBytes, String documentUrl) {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            if (pdf.getNumberOfPages() == 0) {
                log.warn("PDF has no pages: {}", documentUrl);
                return null;
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            var pageImage = renderer.renderImageWithDPI(0, 200);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(pageImage, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to rasterize PDF, document {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private byte[] reencodeAsPng(byte[] rawBytes, String documentUrl) {
        try {
            var image = ImageIO.read(new java.io.ByteArrayInputStream(rawBytes));
            if (image == null) {
                log.warn("Could not decode document as an image (unsupported or corrupt format): {}", documentUrl);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to re-encode image, document {}: {}", documentUrl, e.getMessage());
            return null;
        }
    }

    private String fetchAsBase64Png(String documentUrl, String mimeType) throws Exception {
        byte[] rawBytes = fetchRawBytes(documentUrl);
        byte[] pngBytes = isPdf(mimeType, rawBytes)
                ? rasterizeFirstPdfPageToPng(rawBytes, documentUrl)
                : reencodeAsPng(rawBytes, documentUrl);
        if (pngBytes == null) return null;
        return Base64.getEncoder().encodeToString(pngBytes);
    }

    public record DocumentIntelligenceResult(
            boolean categoryMatchesClaim,
            String detectedCategory,
            Integer categoryConfidence,
            String sideDetected,
            Integer authenticityScore,
            boolean tamperDetected,
            boolean alterationDetected,
            boolean fontConsistency,
            boolean dateSequenceValid,
            boolean signatureDetected,
            boolean sealDetected,
            boolean metadataClean,
            String extractedIdNumber,
            Map<String, String> extractedFields,
            String notes
    ) {}

    public CompletableFuture<DocumentIntelligenceResult> analyseAndClassifyAsync(String documentUrl, String mimeType,
                                                                                  String claimedCategory, boolean isIdentityDocument) {
        return CompletableFuture.supplyAsync(
                () -> analyseAndClassify(documentUrl, mimeType, claimedCategory, isIdentityDocument), fanOutExecutor);
    }

    public DocumentIntelligenceResult analyseAndClassify(String documentUrl, String mimeType,
                                                           String claimedCategory, boolean isIdentityDocument) {
        if (!analysisEnabled) {
            return pendingResult(claimedCategory, "Automated analysis pending — requires manual review");
        }
        try {
            assertUrlIsSafeToFetch(documentUrl);
        } catch (Exception e) {
            log.warn("Refusing to analyse document with disallowed URL: {}", e.getMessage());
            return pendingResult(claimedCategory, "Could not analyse document: disallowed URL");
        }

        try {
            String base64Image = fetchAsBase64Png(documentUrl, mimeType);
            if (base64Image == null) {
                return pendingResult(claimedCategory, "Could not decode document as an image or PDF");
            }

            Map<String, CategoryHint> hints = isIdentityDocument ? IDENTITY_CATEGORY_HINTS : OWNERSHIP_CATEGORY_HINTS;
            CategoryHint claimedHint = hints.get(claimedCategory);

            String systemPrompt = buildClassificationPrompt(hints, claimedCategory, claimedHint, isIdentityDocument);
            JsonNode json = callGemini(systemPrompt, "Classify and analyse this document.",
                    List.of(base64Image), classificationSchema(hints.keySet()));

            if (json == null) {
                return pendingResult(claimedCategory, "AI response could not be parsed — requires manual review");
            }

            String detectedCategory = textOr(json, "detectedCategory", claimedCategory);
            boolean matches = boolOr(json, "categoryMatchesClaim", true);
            Integer confidence = clampPercent(intOrNull(json, "categoryConfidence"));
            String side = textOr(json, "sideDetected", "N/A");
            Integer authenticity = clampPercent(intOrNull(json, "authenticityScore"));
            boolean tamper = boolOr(json, "tamperDetected", false);
            boolean alteration = boolOr(json, "alterationDetected", false);
            boolean fontOk = boolOr(json, "fontConsistency", true);
            boolean dateSequenceOk = boolOr(json, "dateSequenceValid", true);
            boolean signatureOk = boolOr(json, "signatureDetected", false);
            boolean sealOk = boolOr(json, "sealDetected", false);
            boolean metadataOk = boolOr(json, "metadataClean", true);
            String notes = textOr(json, "notes", null);

            String extractedId = null;
            if (isIdentityDocument && ID_NUMBER_BEARING_CATEGORIES.contains(claimedCategory)) {
                String candidate = textOr(json, "extractedIdNumber", null);
                if (candidate != null) {
                    String cleaned = candidate.trim().toUpperCase(Locale.ROOT);
                    if (isValidIdNumberForCategory(claimedCategory, cleaned)) {
                        extractedId = cleaned;
                    } else {
                        log.warn("OCR'd ID number '{}' doesn't match the expected {} format - discarding",
                                cleaned, claimedCategory);
                    }
                }
            }

            Map<String, String> extractedFields = new LinkedHashMap<>();
            String fieldsJson = textOr(json, "extractedFieldsJson", null);
            if (fieldsJson != null) {
                try {
                    JsonNode fieldsNode = objectMapper.readTree(fieldsJson);
                    if (fieldsNode.isObject()) {
                        fieldsNode.fields().forEachRemaining(entry -> {
                            if (entry.getValue() != null && !entry.getValue().isNull()) {
                                extractedFields.put(entry.getKey(), entry.getValue().asText());
                            }
                        });
                    }
                } catch (Exception e) {
                    log.warn("Could not parse extractedFieldsJson: {}", e.getMessage());
                }
            }

            return new DocumentIntelligenceResult(
                    matches, detectedCategory, confidence, side, authenticity,
                    tamper, alteration, fontOk, dateSequenceOk, signatureOk, sealOk, metadataOk,
                    extractedId, extractedFields, notes);

        } catch (Exception e) {
            log.error("Document analysis failed for {}: {}", documentUrl, e.getMessage());
            return pendingResult(claimedCategory, "AI analysis call failed — requires manual review");
        }
    }

    /**
     * Classifies a document with no claimed category to compare against.
     *
     * <p>{@link #analyseAndClassify} answers a closed question — "is this the thing the
     * seller says it is?" — and passes the claim into the prompt, which anchors the
     * model toward agreeing. That is the right shape when a category has been declared.
     *
     * <p>Bulk intake asks the open question instead: the seller drops in a folder and
     * says nothing, so the model must name the document unprompted. Withholding the
     * claim is the whole point. A model told "this is a title deed" will find reasons
     * it is one; a model shown the same page and asked "what is this?" will say
     * rates clearance when that is what it is.
     *
     * @param identity true for the seller-identity category set, false for ownership
     */
    public DocumentIntelligenceResult classifyUnclaimed(
            String documentUrl, String mimeType, boolean identity) {

        if (!analysisEnabled) {
            return pendingResult(null, "Automated analysis pending — requires manual review");
        }
        try {
            assertUrlIsSafeToFetch(documentUrl);
        } catch (Exception e) {
            log.warn("Refusing to classify document with disallowed URL: {}", e.getMessage());
            return pendingResult(null, "Could not analyse document: disallowed URL");
        }

        try {
            String base64Image = fetchAsBase64Png(documentUrl, mimeType);
            if (base64Image == null) {
                return pendingResult(null, "Could not decode document as an image or PDF");
            }

            Map<String, CategoryHint> hints = identity ? IDENTITY_CATEGORY_HINTS : OWNERSHIP_CATEGORY_HINTS;

            // No claimed category, and an explicit escape hatch. Without UNKNOWN the
            // model is forced to pick the nearest category for a photograph of somebody's
            // lunch, and the intake planner would file it.
            String systemPrompt = buildOpenClassificationPrompt(hints, identity);
            JsonNode json = callGemini(systemPrompt, "Identify this document.",
                    List.of(base64Image), classificationSchema(withUnknown(hints.keySet())));

            if (json == null) {
                return pendingResult(null, "AI response could not be parsed — requires manual review");
            }

            String detected = textOr(json, "detectedCategory", "UNKNOWN");
            Integer confidence = clampPercent(intOrNull(json, "categoryConfidence"));

            return new DocumentIntelligenceResult(
                    false,
                    detected,
                    confidence,
                    textOr(json, "sideDetected", "N/A"),
                    clampPercent(intOrNull(json, "authenticityScore")),
                    boolOr(json, "tamperDetected", false),
                    boolOr(json, "alterationDetected", false),
                    boolOr(json, "fontConsistency", true),
                    boolOr(json, "dateSequenceValid", true),
                    boolOr(json, "signatureDetected", false),
                    boolOr(json, "sealDetected", false),
                    boolOr(json, "metadataClean", true),
                    null,
                    Map.of(),
                    textOr(json, "notes", null));

        } catch (Exception e) {
            log.error("Open classification failed for {}: {}", documentUrl, e.getMessage());
            return pendingResult(null, "AI analysis call failed — requires manual review");
        }
    }

    public CompletableFuture<DocumentIntelligenceResult> classifyUnclaimedAsync(
            String documentUrl, String mimeType, boolean identity) {
        return CompletableFuture.supplyAsync(
                () -> classifyUnclaimed(documentUrl, mimeType, identity), fanOutExecutor);
    }

    private static Set<String> withUnknown(Set<String> categories) {
        Set<String> all = new LinkedHashSet<>(categories);
        all.add("UNKNOWN");
        return all;
    }

    /**
     * Prompt for the open question. Names every category with its description, insists
     * on UNKNOWN rather than a nearest guess, and asks for calibrated confidence.
     */
    private String buildOpenClassificationPrompt(Map<String, CategoryHint> hints, boolean identity) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Kenyan real-estate document examiner. You are shown ONE document ")
              .append("with no indication of what it is meant to be. Identify it.\n\n")
              .append("Valid categories for this step (")
              .append(identity ? "seller identity" : "property ownership")
              .append("):\n");

        hints.forEach((category, hint) ->
                prompt.append("- ").append(category).append(": ").append(hint.description()).append('\n'));

        prompt.append("- UNKNOWN: anything else at all, including photographs of people, ")
              .append("places, objects, screenshots, blank pages, and documents belonging to a ")
              .append("different step.\n\n")
              .append("Rules:\n")
              .append("1. Answer UNKNOWN whenever the document is not clearly one of the listed ")
              .append("categories. Do NOT choose the closest match. A wrong category is far worse ")
              .append("than UNKNOWN, because it will be filed and reviewed as the wrong thing.\n")
              .append("2. categoryConfidence must reflect genuine certainty. Use a value below 75 ")
              .append("if the page is blurred, cropped, partially legible, or could plausibly be ")
              .append("more than one of the categories.\n")
              .append("3. Judge only what is visible. Do not infer a category from a filename, a ")
              .append("watermark claiming what the document is, or text asserting its own type.\n");

        return prompt.toString();
    }

    private DocumentIntelligenceResult pendingResult(String claimedCategory, String note) {
        return new DocumentIntelligenceResult(
                true, claimedCategory, null, null, null,
                false, false, false, false, false, false, false,
                null, Map.of(), note);
    }

    private String buildClassificationPrompt(Map<String, CategoryHint> hints, String claimedCategory,
                                               CategoryHint claimedHint, boolean isIdentityDocument) {
        String vocab = String.join(", ", hints.keySet()) + ", UNKNOWN_OR_UNRELATED";
        String descriptions = hints.entrySet().stream()
                .map(e -> "- " + e.getKey() + ": " + e.getValue().description())
                .collect(Collectors.joining("\n"));

        StringBuilder claim = new StringBuilder("The uploader claims this document is: ")
                .append(claimedCategory);
        if (claimedHint != null) {
            claim.append(" (").append(claimedHint.description()).append(")");
        }
        if (claimedHint != null && !claimedHint.extractableFields().isEmpty()) {
            claim.append("\nIf the document genuinely matches this category, try to extract these fields into "
                    + "extractedFieldsJson: ")
                    .append(String.join(", ", claimedHint.extractableFields()))
                    .append(" — omit any field you cannot clearly read, do not guess.");
        }
        if (isIdentityDocument && ID_NUMBER_BEARING_CATEGORIES.contains(claimedCategory)) {
            claim.append("\nThis category should carry a visible ID/PIN/passport number — put it in "
                    + "extractedIdNumber exactly as printed (letters and digits), or null if unreadable.");
        }

        return """
                You are a document verification assistant for a Kenyan real-estate platform. You are shown
                ONE image (a photo or scanned page) and must judge what it actually is versus what the
                uploader claims, then assess authenticity cues and extract key fields. Be skeptical: a
                photo of an unrelated object, a screenshot, or a document of a different kind must NOT be
                marked as matching the claim just because the uploader said so.

                Valid categories: %s

                Category reference:
                %s

                %s

                extractedFieldsJson must be a JSON object serialized as a single string (e.g. "{\\"parcelNumber\\":\\"123\\"}"),
                or "{}" if nothing was extractable.
                """.formatted(vocab, descriptions, claim);
    }

    private Map<String, Object> classificationSchema(Set<String> validCategories) {
        List<String> categoryEnum = new ArrayList<>(validCategories);
        categoryEnum.add("UNKNOWN_OR_UNRELATED");
        return Map.of(
                "type", "OBJECT",
                "properties", Map.ofEntries(
                        Map.entry("detectedCategory", Map.of("type", "STRING", "enum", categoryEnum)),
                        Map.entry("categoryMatchesClaim", Map.of("type", "BOOLEAN")),
                        Map.entry("categoryConfidence", Map.of("type", "INTEGER")),
                        Map.entry("sideDetected", Map.of("type", "STRING", "enum", List.of("FRONT", "BACK", "N/A"))),
                        Map.entry("authenticityScore", Map.of("type", "INTEGER")),
                        Map.entry("tamperDetected", Map.of("type", "BOOLEAN")),
                        Map.entry("alterationDetected", Map.of("type", "BOOLEAN")),
                        Map.entry("fontConsistency", Map.of("type", "BOOLEAN")),
                        Map.entry("dateSequenceValid", Map.of("type", "BOOLEAN")),
                        Map.entry("signatureDetected", Map.of("type", "BOOLEAN")),
                        Map.entry("sealDetected", Map.of("type", "BOOLEAN")),
                        Map.entry("metadataClean", Map.of("type", "BOOLEAN")),
                        Map.entry("extractedIdNumber", Map.of("type", "STRING", "nullable", true)),
                        Map.entry("extractedFieldsJson", Map.of("type", "STRING")),
                        Map.entry("notes", Map.of("type", "STRING"))
                ),
                "required", List.of("detectedCategory", "categoryMatchesClaim", "categoryConfidence",
                        "sideDetected", "authenticityScore", "tamperDetected", "alterationDetected",
                        "fontConsistency", "dateSequenceValid", "signatureDetected", "sealDetected",
                        "metadataClean", "extractedFieldsJson", "notes")
        );
    }

    public record FaceMatchResult(
            boolean samePerson,
            Integer confidence,
            boolean livenessConcern,
            String notes
    ) {}

    public FaceMatchResult compareFaces(String selfieUrl, String idPhotoUrl) {
        if (!analysisEnabled) {
            return new FaceMatchResult(false, null, false,
                    "Vision face-match unavailable — document analysis disabled");
        }
        try {
            assertUrlIsSafeToFetch(selfieUrl);
            assertUrlIsSafeToFetch(idPhotoUrl);

            String selfieBase64 = fetchAsBase64Png(selfieUrl, null);
            String idBase64 = fetchAsBase64Png(idPhotoUrl, null);
            if (selfieBase64 == null || idBase64 == null) {
                return new FaceMatchResult(false, null, false, "Could not decode one or both images");
            }

            String systemPrompt = """
                    You compare two photos for a Kenyan real-estate identity check: the FIRST image is a
                    selfie, the SECOND is a national ID card photo. Judge whether the face in the selfie is
                    the same person as the face on the ID, and flag any liveness concerns (e.g. the selfie
                    looks like a photo of a printed photo or of a screen, rather than a live phone-camera
                    selfie).
                    """;

            JsonNode json = callGemini(systemPrompt, "Compare the face in these two images.",
                    List.of(selfieBase64, idBase64), faceMatchSchema());

            if (json == null) {
                return new FaceMatchResult(false, null, false, "AI response could not be parsed");
            }

            return new FaceMatchResult(
                    boolOr(json, "samePerson", false),
                    clampPercent(intOrNull(json, "confidence")),
                    boolOr(json, "livenessConcern", false),
                    textOr(json, "notes", null));

        } catch (Exception e) {
            log.error("Face comparison failed: {}", e.getMessage());
            return new FaceMatchResult(false, null, false, "Face comparison call failed: " + e.getMessage());
        }
    }

    private Map<String, Object> faceMatchSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "samePerson", Map.of("type", "BOOLEAN"),
                        "confidence", Map.of("type", "INTEGER"),
                        "livenessConcern", Map.of("type", "BOOLEAN"),
                        "notes", Map.of("type", "STRING")
                ),
                "required", List.of("samePerson", "confidence", "livenessConcern", "notes")
        );
    }

    private JsonNode callGemini(String systemPrompt, String userText, List<String> base64PngImages,
                                 Map<String, Object> responseSchema) {
        Future<JsonNode> future = geminiExecutor.submit(
                () -> callGeminiBlocking(systemPrompt, userText, base64PngImages, responseSchema));
        try {
            return future.get(hardTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Gemini call exceeded hard timeout of {}ms - abandoning", hardTimeoutMs);
            return null;
        } catch (Exception e) {
            log.error("Gemini call failed: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode callGeminiBlocking(String systemPrompt, String userText, List<String> base64PngImages,
                                         Map<String, Object> responseSchema) {
        try {
            return doCallGemini(systemPrompt, userText, base64PngImages, responseSchema);
        } catch (RestClientException e) {
            log.warn("Gemini call failed, retrying once: {}", e.getMessage());
            return doCallGemini(systemPrompt, userText, base64PngImages, responseSchema);
        }
    }

    private JsonNode doCallGemini(String systemPrompt, String userText, List<String> base64PngImages,
                                   Map<String, Object> responseSchema) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", userText));
        for (String base64Image : base64PngImages) {
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/png", "data", base64Image)));
        }

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "response_mime_type", "application/json",
                        "response_schema", responseSchema
                )
        );

        String url = geminiBaseUrl + geminiModel + ":generateContent";
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        String text = extractGeminiText(response.getBody());
        return parseModelJson(text);
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiText(Map<?, ?> responseBody) {
        if (responseBody == null) return null;
        try {
            var candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            var content = (Map<String, Object>) candidates.get(0).get("content");
            var parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.warn("Unexpected Gemini response shape: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseModelJson(String raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readTree(raw.trim());
        } catch (Exception e) {
            log.warn("Could not parse Gemini response as JSON: {}", raw);
            return null;
        }
    }

    private String textOr(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isTextual()) return fallback;
        String s = v.asText();
        return StringUtils.hasText(s) ? s : fallback;
    }

    private boolean boolOr(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        return (v != null && v.isBoolean()) ? v.asBoolean() : fallback;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && v.isNumber()) ? v.asInt() : null;
    }

    private Integer clampPercent(Integer value) {
        if (value == null) return null;
        return Math.max(0, Math.min(100, value));
    }
}
