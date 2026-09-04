package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateTenantRequest {
    @NotBlank @Size(max = 160) private String fullName;
    @NotBlank @Size(max = 32) private String phone;
    @Email @Size(max = 255) private String email;
    @Size(max = 40) private String nationalId;
    @Size(max = 160) private String emergencyName;
    @Size(max = 32) private String emergencyPhone;
}
