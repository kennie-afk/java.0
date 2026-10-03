package com.hms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.platform.storage.LocalObjectStore;
import com.hms.support.IntegrationTest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class ImagingAttachmentTest extends IntegrationTest {

    private static byte[] image(String format, int w, int h, int shade) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(shade % 256, (shade * 7) % 256, (shade * 13) % 256));
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder upload(String orderId, String token, byte[] data, String name, String contentType) {
        return (MockMultipartHttpServletRequestBuilder) MockMvcRequestBuilders.multipart("/v1/imaging/orders/" + orderId + "/attachments")
                .file(new MockMultipartFile("file", name, contentType, data)).header("Authorization", "Bearer " + token);
    }

    /** Changes the first signature character to a different one, so the test cannot pass by luck. */
    private static String tamper(String url) {
        int i = url.indexOf("sig=") + 4;
        return url.substring(0, i) + (url.charAt(i) == 'A' ? 'B' : 'A') + url.substring(i + 1);
    }

    private String performedOrder(Org org, String doctor, String radiographer) throws Exception {
        String cxr = create("/v1/imaging/procedures", org.token(), Map.of("code", "CXR", "name", "Chest X-ray", "modality", "XR", "price", 1500)).get("id").asText();
        UUID p = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Img", "Att" + UUID.randomUUID().toString().substring(0, 6), "1975-05-05")).get("id").asText());
        String id = create("/v1/imaging/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "procedureId", cxr)).get("id").asText();
        sendJson(post("/v1/imaging/orders/" + id + "/perform", radiographer), Map.of("techniqueNote", "PA erect"), 200);
        return id;
    }

    @Test
    void imagesAttachToAPerformedStudyAndAreServedOnlyThroughASignedLink() throws Exception {
        Org org = newOrg("imgatt");
        String doctor = userWithRole(org, "DOCTOR");
        String radiographer = userWithRole(org, "RADIOGRAPHER", "RADIOGRAPHER");
        String radiologist = userWithRole(org, "RADIOLOGIST");
        String cxr = create("/v1/imaging/procedures", org.token(), Map.of("code", "CXR", "name", "Chest X-ray", "modality", "XR", "price", 1500)).get("id").asText();
        UUID p = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Img", "Att" + UUID.randomUUID().toString().substring(0, 6), "1975-05-05")).get("id").asText());
        String id = create("/v1/imaging/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "procedureId", cxr)).get("id").asText();
        byte[] png = image("png", 64, 48, 40);

        // Nothing to attach before the study is performed.
        send(upload(id, radiographer, png, "a.png", "image/png"), 409);
        send(post("/v1/imaging/orders/" + id + "/perform", radiographer), 200);

        JsonNode a = json.readTree(send(upload(id, radiographer, png, "chest.png", "image/png").param("caption", "PA erect"), 201).toString());
        assertThat(a.get("contentType").asText()).isEqualTo("image/png");
        assertThat(a.get("width").asInt()).isEqualTo(64);
        assertThat(a.get("height").asInt()).isEqualTo(48);
        assertThat(a.get("sha256").asText()).hasSize(64);
        assertThat(a.get("caption").asText()).isEqualTo("PA erect");

        // The link serves the exact bytes, hardened, with no sign-in; a changed signature or type is refused with the same answer as a missing file.
        String url = a.get("url").asText();
        assertThat(url).startsWith("/v1/objects/" + org.orgId() + "/");
        var res = mvc.perform(MockMvcRequestBuilders.get(url)).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(png);
        assertThat(res.getContentType()).isEqualTo("image/png");
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(res.getHeader("Cache-Control")).contains("no-store");
        assertThat(res.getHeader("Content-Security-Policy")).contains("sandbox");
        assertThat(mvc.perform(MockMvcRequestBuilders.get(tamper(url))).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(MockMvcRequestBuilders.get(url.replace("&t=png", "&t=jpeg"))).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(MockMvcRequestBuilders.get(url.replaceAll("exp=\\d+", "exp=9999999999"))).andReturn().getResponse().getStatus()).isEqualTo(404);

        // The same picture twice, a different type of file, DICOM, a broken image and an oversized one are all refused.
        send(upload(id, radiographer, png, "again.png", "image/png"), 409);
        JsonNode jpeg = json.readTree(send(upload(id, radiographer, image("jpg", 80, 60, 90), "x.jpg", "image/jpeg"), 201).toString());
        assertThat(jpeg.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(send(upload(id, radiographer, "hello world".getBytes(), "x.png", "image/png"), 400).get("code").asText()).isEqualTo("unsupported_image");
        byte[] dicom = new byte[200];
        dicom[128] = 'D'; dicom[129] = 'I'; dicom[130] = 'C'; dicom[131] = 'M';
        assertThat(send(upload(id, radiographer, dicom, "x.dcm", "application/dicom"), 400).get("code").asText()).isEqualTo("dicom_not_supported");
        byte[] broken = new byte[400];
        System.arraycopy(png, 0, broken, 0, 24);
        assertThat(send(upload(id, radiographer, broken, "b.png", "image/png"), 400).get("code").asText()).isEqualTo("unreadable_image");
        byte[] big = new byte[8 * 1024 * 1024 + 1];
        System.arraycopy(png, 0, big, 0, 16);
        assertThat(send(upload(id, radiographer, big, "big.png", "image/png"), 400).get("code").asText()).isEqualTo("file_too_large");
        assertThat(send(upload(id, radiographer, image("png", 10, 10, 5), "c.png", "image/png").param("caption", "x".repeat(201)), 400).get("code").asText()).isEqualTo("caption_too_long");

        // Who may do what: the ordering doctor can look but not attach; a stranger's organisation sees nothing.
        assertThat(fetch("/v1/imaging/orders/" + id + "/attachments", doctor)).hasSize(2);
        send(upload(id, doctor, image("png", 11, 11, 6), "d.png", "image/png"), 403);
        Org other = newOrg("imgatt2");
        send(get("/v1/imaging/orders/" + id + "/attachments", other.token()), 404);
        send(upload(id, other.token(), image("png", 12, 12, 7), "e.png", "image/png"), 404);

        // A wrong image is withdrawn with a reason: it leaves the list, the row and file stay, and it cannot be withdrawn twice.
        String first = a.get("id").asText();
        sendJson(post("/v1/imaging/orders/" + id + "/attachments/" + first + "/remove", radiographer), Map.of("reason", "no"), 400);
        sendJson(post("/v1/imaging/orders/" + id + "/attachments/" + first + "/remove", radiographer), Map.of("reason", "Wrong patient's image"), 200);
        sendJson(post("/v1/imaging/orders/" + id + "/attachments/" + first + "/remove", radiographer), Map.of("reason", "Wrong patient's image"), 404);
        assertThat(fetch("/v1/imaging/orders/" + id + "/attachments", doctor)).hasSize(1);
        // The withdrawn picture can be attached again (it is a different row), but not while the report is signed.
        send(upload(id, radiographer, image("png", 13, 13, 8), "f.png", "image/png"), 201);
        sendJson(post("/v1/imaging/orders/" + id + "/report", radiographer), Map.of("findings", "Clear lungs", "impression", "Normal study"), 200);
        send(post("/v1/imaging/orders/" + id + "/sign", radiologist), 200);
        send(upload(id, radiographer, image("png", 14, 14, 9), "g.png", "image/png"), 409);
        sendJson(post("/v1/imaging/orders/" + id + "/attachments/" + jpeg.get("id").asText() + "/remove", radiographer), Map.of("reason", "Changed my mind"), 409);

        // Viewing and attaching are on the audit trail, and the trail still verifies.
        String trail = fetch("/v1/audit/events?entityType=patient&entityId=" + p, org.token()).toString();
        assertThat(trail).contains("imaging.attachment.view").contains("imaging.attachment.add").contains("imaging.attachment.remove");
        assertThat(fetch("/v1/audit/verify", org.token()).findValuesAsText("intact")).containsOnly("true");
    }

    @Test
    void theTableAcceptsNothingButAWithdrawalEvenFromTheOwner() throws Exception {
        Org org = newOrg("imgatt3");
        String doctor = userWithRole(org, "DOCTOR");
        String radiographer = userWithRole(org, "RADIOGRAPHER", "RADIOGRAPHER");
        String id = performedOrder(org, doctor, radiographer);
        String att = json.readTree(send(upload(id, radiographer, image("png", 20, 20, 3), "a.png", "image/png"), 201).toString()).get("id").asText();
        assertThrows(Exception.class, () -> asOwner("UPDATE imaging_attachments SET size_bytes = 5 WHERE id = '" + att + "'"));
        assertThrows(Exception.class, () -> asOwner("UPDATE imaging_attachments SET sha256 = repeat('a', 64) WHERE id = '" + att + "'"));
        assertThrows(Exception.class, () -> asOwner("DELETE FROM imaging_attachments WHERE id = '" + att + "'"));
        asOwner("UPDATE imaging_attachments SET removed_at = now(), removed_by = uploaded_by, removed_reason = 'wrong study' WHERE id = '" + att + "'");
        assertThrows(Exception.class, () -> asOwner("UPDATE imaging_attachments SET removed_reason = 'changed' WHERE id = '" + att + "'"));
    }

    @Test
    void theLocalStoreRefusesBadKeysAndExpiredOrTamperedLinks() throws Exception {
        Path dir = Files.createTempDirectory("hms-store");
        LocalObjectStore store = new LocalObjectStore(dir, "k".repeat(32).getBytes());
        String key = UUID.randomUUID() + "/" + UUID.randomUUID();
        store.put(key, new byte[] {1, 2, 3});
        assertThat(store.read(key)).contains(new byte[] {1, 2, 3});
        assertThat(store.read(UUID.randomUUID() + "/" + UUID.randomUUID())).isEmpty();
        for (String bad : new String[] {"../etc/passwd", "a/b", key + "/../x", "", key.replace("-", "")}) {
            assertThrows(IllegalArgumentException.class, () -> store.put(bad, new byte[] {1}));
            assertThrows(IllegalArgumentException.class, () -> store.read(bad));
        }
        String url = store.presignGet(key, "image/png", Duration.ofMinutes(5));
        String q = url.substring(url.indexOf('?') + 1);
        long exp = Long.parseLong(q.replaceAll(".*exp=(\\d+).*", "$1"));
        String sig = q.replaceAll(".*sig=([^&]+).*", "$1");
        assertThat(store.verify(key, exp, "png", sig)).isTrue();
        assertThat(store.verify(key, exp + 1, "png", sig)).isFalse();
        assertThat(store.verify(key, exp, "jpeg", sig)).isFalse();
        assertThat(store.verify(UUID.randomUUID() + "/" + UUID.randomUUID(), exp, "png", sig)).isFalse();
        // A link that has expired is refused even with a genuine signature.
        String old = store.presignGet(key, "image/png", Duration.ofSeconds(-5));
        String oq = old.substring(old.indexOf('?') + 1);
        assertThat(store.verify(key, Long.parseLong(oq.replaceAll(".*exp=(\\d+).*", "$1")), "png", oq.replaceAll(".*sig=([^&]+).*", "$1"))).isFalse();
        // Another store with a different secret does not honour the link.
        assertThat(new LocalObjectStore(dir, "z".repeat(32).getBytes()).verify(key, exp, "png", sig)).isFalse();
        assertThrows(IllegalArgumentException.class, () -> store.presignGet(key, "text/html", Duration.ofMinutes(1)));
    }
}
