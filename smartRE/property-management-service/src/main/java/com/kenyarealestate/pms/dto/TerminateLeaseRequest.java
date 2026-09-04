package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TerminateLeaseRequest {
    @NotBlank @Size(max = 1000) private String reason;
}
