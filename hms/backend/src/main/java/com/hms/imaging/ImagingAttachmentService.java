package com.hms.imaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.platform.audit.AuditService;
import com.hms.platform.storage.ObjectStore;
import com.hms.platform.tenancy.TenantContext;
import com.hms.platform.web.ApiException;
import com.hms.registry.PatientAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Images attached to a performed study. PNG and JPEG only; DICOM is not handled and a DICOM file is refused, not stored.
 *
 * <p>What is stored is decided from the file's own bytes, never from the name or the type the client claims, and the picture is
 * measured without being decoded, so a small file that claims to be a gigantic image cannot exhaust memory.
 */
@Service
public class ImagingAttachmentService {

    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_PER_ORDER = 30;
    /** 100 megapixels: far beyond any radiograph a screen can show, and the point where decoding would start to cost real memory. */
    static final long MAX_PIXELS = 100_000_000L;
    static final Duration LINK_TTL = Duration.ofMinutes(5);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(UUID id, String contentType, int sizeBytes, int width, int height, String sha256, String caption, Instant uploadedAt, String url) {}

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final PatientAccess patients;
    private final ObjectStore store;

    private final boolean uploadsEnabled;

    public ImagingAttachmentService(JdbcClient jdbc, AuditService audit, PatientAccess patients, ObjectStore store,
                                    @org.springframework.beans.factory.annotation.Value("${hms.storage.uploads:true}") boolean uploadsEnabled) {
        this.uploadsEnabled = uploadsEnabled;
        this.jdbc = jdbc;
        this.audit = audit;
        this.patients = patients;
        this.store = store;
    }

    private record Order(UUID facilityId, UUID patientId, String status) {}

    private Order lockOrder(UUID orderId) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = jdbc.sql("SELECT facility_id, patient_id, status FROM imaging_orders WHERE org_id = ? AND id = ? FOR UPDATE").params(t.orgId(), orderId)
                .query((rs, n) -> new Order(rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status")))
                .optional().orElseThrow(() -> ApiException.notFound("Imaging order"));
        t.requireFacility(o.facilityId());
        patients.requireAlive(o.patientId());
        return o;
    }

    private static void requireOpenForImages(Order o) {
        switch (o.status()) {
            case "ORDERED" -> throw ApiException.conflict("not_performed", "Images are attached once the study has been performed.");
            case "CANCELLED" -> throw ApiException.conflict("order_cancelled", "That study was cancelled.");
            case "SIGNED" -> throw ApiException.conflict("report_signed", "The report is signed, so its images are fixed. Amend the report first.");
            default -> { }
        }
    }

    @Transactional
    public Attachment add(UUID orderId, byte[] data, String caption) {
        TenantContext.Tenant t = TenantContext.require();
        if (!uploadsEnabled) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_IMPLEMENTED, "uploads_disabled",
                    "Image uploads are switched off in this deployment because it has no durable shared storage. Existing images can still be viewed.");
        }
        Order o = lockOrder(orderId);
        requireOpenForImages(o);
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("empty_file", "The file is empty.");
        }
        if (data.length > MAX_BYTES) {
            throw ApiException.badRequest("file_too_large", "Images can be up to 8 MB. Resize or export a smaller copy.");
        }
        String type = sniff(data);
        int[] size = measure(data);
        if ((long) size[0] * size[1] > MAX_PIXELS) {
            throw ApiException.badRequest("image_too_large", "That image is " + size[0] + " by " + size[1] + " pixels, which is more than 100 megapixels.");
        }
        if (caption != null && caption.length() > 200) {
            throw ApiException.badRequest("caption_too_long", "The caption can be up to 200 characters.");
        }
        if (jdbc.sql("SELECT count(*) FROM imaging_attachments WHERE org_id = ? AND order_id = ? AND removed_at IS NULL").params(t.orgId(), orderId).query(Long.class).single() >= MAX_PER_ORDER) {
            throw ApiException.conflict("too_many_images", "A study can hold " + MAX_PER_ORDER + " images.");
        }
        String sha = sha256(data);
        if (jdbc.sql("SELECT count(*) FROM imaging_attachments WHERE org_id = ? AND order_id = ? AND sha256 = ?").params(t.orgId(), orderId, sha).query(Long.class).single() > 0) {
            throw ApiException.conflict("duplicate_image", "That image is already attached to this study.");
        }
        UUID id = UUID.randomUUID();
        // The file goes in first. If the row then fails, an unreferenced file is harmless; a row pointing at nothing would not be.
        store.put(t.orgId() + "/" + id, data);
        jdbc.sql("""
                INSERT INTO imaging_attachments (id, org_id, order_id, facility_id, content_type, size_bytes, width, height, sha256, caption, uploaded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(id, t.orgId(), orderId, o.facilityId(), type, data.length, size[0], size[1], sha, caption == null || caption.isBlank() ? null : caption.trim(), t.practitionerId()).update();
        audit.record("imaging.attachment.add", "patient", o.patientId(), o.facilityId(), null, Map.of("order", orderId.toString(), "attachment", id.toString(), "sha256", sha));
        return one(id);
    }

    /** The study's images with short-lived links to fetch them. Listing is itself audited: these are patient images. */
    @Transactional
    public List<Attachment> list(UUID orderId) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = jdbc.sql("SELECT facility_id, patient_id, status FROM imaging_orders WHERE org_id = ? AND id = ?").params(t.orgId(), orderId)
                .query((rs, n) -> new Order(rs.getObject("facility_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("status")))
                .optional().orElseThrow(() -> ApiException.notFound("Imaging order"));
        t.requireFacility(o.facilityId());
        patients.require(o.patientId());
        List<Attachment> rows = jdbc.sql(SELECT + " WHERE org_id = ? AND order_id = ? AND removed_at IS NULL ORDER BY uploaded_at, id").params(t.orgId(), orderId)
                .query((rs, n) -> map(rs, t.orgId())).list();
        if (!rows.isEmpty()) {
            audit.record("imaging.attachment.view", "patient", o.patientId(), o.facilityId(), null, Map.of("order", orderId.toString(), "count", rows.size()));
        }
        return rows;
    }

    @Transactional
    public void remove(UUID orderId, UUID attachmentId, String reason) {
        TenantContext.Tenant t = TenantContext.require();
        Order o = lockOrder(orderId);
        requireOpenForImages(o);
        int n = jdbc.sql("UPDATE imaging_attachments SET removed_at = now(), removed_by = ?, removed_reason = ? WHERE org_id = ? AND id = ? AND order_id = ? AND removed_at IS NULL")
                .params(t.practitionerId(), reason.trim(), t.orgId(), attachmentId, orderId).update();
        if (n == 0) {
            throw ApiException.notFound("Attachment");
        }
        audit.record("imaging.attachment.remove", "patient", o.patientId(), o.facilityId(), reason.trim(), Map.of("order", orderId.toString(), "attachment", attachmentId.toString()));
    }

    private Attachment one(UUID id) {
        TenantContext.Tenant t = TenantContext.require();
        return jdbc.sql(SELECT + " WHERE org_id = ? AND id = ?").params(t.orgId(), id).query((rs, n) -> map(rs, t.orgId())).single();
    }

    private static final String SELECT = "SELECT id, content_type, size_bytes, width, height, sha256, caption, uploaded_at FROM imaging_attachments";

    private Attachment map(java.sql.ResultSet rs, UUID orgId) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String type = rs.getString("content_type");
        return new Attachment(id, type, rs.getInt("size_bytes"), rs.getInt("width"), rs.getInt("height"), rs.getString("sha256"), rs.getString("caption"),
                rs.getObject("uploaded_at", OffsetDateTime.class).toInstant(), store.presignGet(orgId + "/" + id, type, LINK_TTL));
    }

    /** PNG or JPEG by the file's own signature. Anything else, DICOM included, is refused. */
    static String sniff(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G' && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 132 && d[128] == 'D' && d[129] == 'I' && d[130] == 'C' && d[131] == 'M') {
            throw ApiException.badRequest("dicom_not_supported", "DICOM files are not supported. Export the key images as PNG or JPEG.");
        }
        throw ApiException.badRequest("unsupported_image", "Only PNG and JPEG images are accepted.");
    }

    /** Width and height from the header, without decoding the picture. A file whose header does not parse is not an image we will keep. */
    static int[] measure(byte[] d) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(d))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("unreadable_image", "That file is not a readable image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1) {
                    throw ApiException.badRequest("unreadable_image", "That file is not a readable image.");
                }
                return new int[] {w, h};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException a) {
                throw a;
            }
            throw ApiException.badRequest("unreadable_image", "That file is not a readable image.");
        }
    }

    private static String sha256(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
