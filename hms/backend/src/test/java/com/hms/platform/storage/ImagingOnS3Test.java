package com.hms.platform.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.support.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The real application with HMS_STORAGE_MODE=s3 against the signature-checking stub: an image uploaded through the API is stored under its tenant's prefix and served through the signed API link. */
class ImagingOnS3Test extends IntegrationTest {

    @DynamicPropertySource
    static void s3(DynamicPropertyRegistry r) {
        r.add("hms.storage.mode", () -> "s3");
        r.add("hms.storage.s3.endpoint", () -> S3ObjectStoreTest.endpoint);
        r.add("hms.storage.s3.bucket", () -> "hms-images");
        r.add("hms.storage.s3.access-key", () -> S3ObjectStoreTest.ACCESS);
        r.add("hms.storage.s3.secret-key", () -> S3ObjectStoreTest.SECRET);
    }

    @Test
    void uploadsWorkOnAnS3StoreAndAreServedThroughTheApi() throws Exception {
        Org org = newOrg("imgs3");
        String doctor = userWithRole(org, "DOCTOR");
        String radiographer = userWithRole(org, "RADIOGRAPHER", "RADIOGRAPHER");
        String cxr = create("/v1/imaging/procedures", org.token(), Map.of("code", "CXR", "name", "Chest X-ray", "modality", "XR", "price", 1500)).get("id").asText();
        UUID p = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Img", "S3" + UUID.randomUUID().toString().substring(0, 6), "1975-05-05")).get("id").asText());
        String id = create("/v1/imaging/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "procedureId", cxr)).get("id").asText();
        send(post("/v1/imaging/orders/" + id + "/perform", radiographer), 200);
        BufferedImage img = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] png = out.toByteArray();

        JsonNode a = send(((org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) MockMvcRequestBuilders
                .multipart("/v1/imaging/orders/" + id + "/attachments").file(new MockMultipartFile("file", "c.png", "image/png", png))
                .header("Authorization", "Bearer " + radiographer)), 201);
        // Stored under the tenant's prefix in the bucket...
        assertThat(S3ObjectStoreTest.objects.keySet()).anyMatch(k -> k.startsWith("/hms-images/" + org.orgId() + "/"));
        // ...and served by the API after it fetches from the bucket, with the same hardening as the local store.
        String url = a.get("url").asText();
        assertThat(url).startsWith("/v1/objects/" + org.orgId() + "/");
        var res = mvc.perform(MockMvcRequestBuilders.get(url)).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(png);
        assertThat(res.getHeader("Cache-Control")).contains("no-store");
        assertThat(mvc.perform(MockMvcRequestBuilders.get(url.replace("sig=", "sig=x"))).andReturn().getResponse().getStatus()).isEqualTo(404);
    }
}
