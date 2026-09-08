package com.kenyarealestate.verification.dto.intake;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * A folder of documents dropped in at once, with no category declared for any of them.
 *
 * <p>That absence is the feature. The existing single-document endpoint requires the
 * seller to name each file's category from a list of sixteen, which is the step people
 * get wrong and the step they abandon.
 */
@Data
public class BulkIntakeRequest {

    @NotEmpty
    @Size(max = 30, message = "Upload at most 30 documents at a time")
    @Valid
    private List<Item> documents;

    @Data
    public static class Item {
        /** Where user-service put the file. */
        @NotBlank private String documentUrl;
        /** For the review screen only; never used to decide a category. */
        @NotBlank private String originalFilename;
        private String mimeType;
        private Long fileSizeBytes;
    }
}
