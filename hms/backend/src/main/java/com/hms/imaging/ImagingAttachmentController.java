package com.hms.imaging;

import static com.hms.imaging.ImagingAttachmentService.Attachment;

import com.hms.platform.rbac.Permissions;
import com.hms.platform.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/imaging/orders/{orderId}/attachments")
class ImagingAttachmentController {

    private static final String READ = "hasAuthority('" + Permissions.IMAGING_READ + "')";
    private static final String PERFORM = "hasAuthority('" + Permissions.IMAGING_PERFORM + "')";

    record RemoveInput(@NotBlank @Size(min = 5, max = 300) String reason) {}

    private final ImagingAttachmentService attachments;

    ImagingAttachmentController(ImagingAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping
    @PreAuthorize(READ)
    List<Attachment> list(@PathVariable UUID orderId) {
        return attachments.list(orderId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PERFORM)
    Attachment add(@PathVariable UUID orderId, @RequestPart("file") MultipartFile file, @RequestParam(required = false) String caption) {
        try {
            return attachments.add(orderId, file.getBytes(), caption);
        } catch (IOException e) {
            throw ApiException.badRequest("upload_failed", "The upload could not be read.");
        }
    }

    @PostMapping("/{attachmentId}/remove")
    @PreAuthorize(PERFORM)
    void remove(@PathVariable UUID orderId, @PathVariable UUID attachmentId, @Valid @RequestBody RemoveInput in) {
        attachments.remove(orderId, attachmentId, in.reason());
    }
}
