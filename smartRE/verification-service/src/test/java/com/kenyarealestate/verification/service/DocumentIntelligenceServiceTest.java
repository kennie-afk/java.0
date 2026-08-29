package com.kenyarealestate.verification.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DocumentIntelligenceServiceTest {

    private DocumentIntelligenceService service;
    private HttpServer allowedHostServer;
    private HttpServer visionServer;
    private int allowedPort;
    private int visionPort;

    @BeforeEach
    void setup() throws Exception {
        allowedHostServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] tinyPngBytes = tinyPng();
        allowedHostServer.createContext("/doc.pdf", exchange -> {
            byte[] body = tinyPngBytes;
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        allowedHostServer.start();
        allowedPort = allowedHostServer.getAddress().getPort();

        visionServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        visionPort = visionServer.getAddress().getPort();

        service = new DocumentIntelligenceService();
        ReflectionTestUtils.setField(service, "analysisEnabled", false);
        ReflectionTestUtils.setField(service, "userServiceUrl", "http://user-service:8081");
        ReflectionTestUtils.setField(service, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(service, "documentUrlAllowedHostsCsv", "127.0.0.1,localhost,user-service");
        ReflectionTestUtils.setField(service, "geminiBaseUrl", "http://127.0.0.1:" + visionPort + "/");
        ReflectionTestUtils.setField(service, "geminiModel", "test-model");
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
    }

    @AfterEach
    void teardown() {
        allowedHostServer.stop(0);
        visionServer.stop(0);
    }

    @Test
    void computeSha256FromUrl_refusesUrlOnDisallowedHost() {
        String maliciousUrl = "http://169.254.169.254/latest/meta-data/iam/security-credentials/";

        String hash = service.computeSha256FromUrl(maliciousUrl);

        assertNull(hash, "must refuse to fetch a URL whose host isn't allowlisted");
    }

    @Test
    void computeSha256FromUrl_refusesNonHttpScheme() {
        String hash = service.computeSha256FromUrl("file:///etc/passwd");

        assertNull(hash);
    }

    @Test
    void computeSha256FromUrl_allowsUrlOnAllowlistedHost() {
        String url = "http://127.0.0.1:" + allowedPort + "/doc.pdf";

        String hash = service.computeSha256FromUrl(url);

        assertNotNull(hash, "an allowlisted host should be fetchable");
        assertEquals(64, hash.length(), "should be a hex-encoded SHA-256 digest");
    }

    @Test
    void computeSha256FromUrl_alwaysAllowsUserServiceDocumentUrls_regardlessOfClaimedHost() {
        String url = "http://evil.example.com/api/documents/files/documents/national_id/abc.pdf";

        String hash = service.computeSha256FromUrl(url);
        assertNull(hash);
    }

    @Test
    void analyseAndClassify_returnsPendingResult_whenDisabled_withoutCallingVisionModel() {
        var result = service.analyseAndClassify(
                "http://127.0.0.1:" + allowedPort + "/doc.pdf", null, "NATIONAL_ID_FRONT", true);

        assertTrue(result.categoryMatchesClaim(), "disabled analysis assumes the claim rather than flagging it");
        assertEquals("NATIONAL_ID_FRONT", result.detectedCategory());
        assertNull(result.authenticityScore());
        assertFalse(result.tamperDetected());
        assertTrue(result.notes().toLowerCase().contains("pending"));
    }

    @Test
    void analyseAndClassify_refusesDisallowedHost_withoutCallingVisionModel() {
        ReflectionTestUtils.setField(service, "analysisEnabled", true);

        var result = service.analyseAndClassify(
                "http://169.254.169.254/latest/meta-data/", null, "NATIONAL_ID_FRONT", true);

        assertTrue(result.categoryMatchesClaim());
        assertNull(result.authenticityScore());
    }

    @Test
    void analyseAndClassify_parsesGeminiStructuredJson_andSurfacesCategoryMismatch() throws Exception {
        ReflectionTestUtils.setField(service, "analysisEnabled", true);

        String modelJson = """
                {
                  "detectedCategory": "UNKNOWN_OR_UNRELATED",
                  "categoryMatchesClaim": false,
                  "categoryConfidence": 93,
                  "sideDetected": "N/A",
                  "authenticityScore": 5,
                  "tamperDetected": false,
                  "alterationDetected": false,
                  "fontConsistency": false,
                  "dateSequenceValid": true,
                  "signatureDetected": false,
                  "sealDetected": false,
                  "metadataClean": true,
                  "extractedIdNumber": null,
                  "extractedFieldsJson": "{}",
                  "notes": "This looks like a photo of a plant, not a Kenyan ID."
                }""";
        String geminiResponseBody = """
                {"candidates":[{"content":{"parts":[{"text": %s}]}}]}
                """.formatted(toJsonString(modelJson));

        visionServer.createContext("/test-model:generateContent", exchange -> {
            byte[] body = geminiResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        visionServer.start();

        var result = service.analyseAndClassify(
                "http://127.0.0.1:" + allowedPort + "/doc.pdf", null, "NATIONAL_ID_FRONT", true);

        assertFalse(result.categoryMatchesClaim());
        assertEquals("UNKNOWN_OR_UNRELATED", result.detectedCategory());
        assertEquals(93, result.categoryConfidence());
        assertNull(result.extractedIdNumber(), "UNKNOWN_OR_UNRELATED result should not carry an ID number");
    }

    private static String toJsonString(String raw) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(raw);
    }

    private static byte[] tinyPng() throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
