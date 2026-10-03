package com.hms;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Where the disk is not durable (the Kubernetes manifests), uploads are refused outright instead of losing images later. */
@TestPropertySource(properties = "hms.storage.uploads=false")
class ImagingUploadsDisabledTest extends IntegrationTest {

    @Test
    void uploadsAreRefusedWhenTheDeploymentHasNoDurableStorage() throws Exception {
        Org org = newOrg("imgoff");
        String doctor = userWithRole(org, "DOCTOR");
        String radiographer = userWithRole(org, "RADIOGRAPHER", "RADIOGRAPHER");
        String proc = create("/v1/imaging/procedures", org.token(), Map.of("code", "CXR", "name", "Chest X-ray", "modality", "XR", "price", 1500)).get("id").asText();
        UUID p = UUID.fromString(create("/v1/patients", org.token(), patient(org.facilityId(), "Img", "Off" + UUID.randomUUID().toString().substring(0, 6), "1975-05-05")).get("id").asText());
        String id = create("/v1/imaging/orders", doctor, Map.of("facilityId", org.facilityId().toString(), "patientId", p.toString(), "procedureId", proc)).get("id").asText();
        send(post("/v1/imaging/orders/" + id + "/perform", radiographer), 200);
        var req = MockMvcRequestBuilders.multipart("/v1/imaging/orders/" + id + "/attachments").file(new MockMultipartFile("file", "a.png", "image/png", new byte[] {1})).header("Authorization", "Bearer " + radiographer);
        assertThat(send(req, 501).get("code").asText()).isEqualTo("uploads_disabled");
        // Looking is still allowed.
        assertThat(fetch("/v1/imaging/orders/" + id + "/attachments", radiographer)).isEmpty();
    }
}
