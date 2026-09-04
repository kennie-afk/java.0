package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateTenantRequest {
    @Size(max = 160) private String fullName;
    @Size(max = 32) private String phone;
    @Email @Size(max = 255) private String email;
    @Size(max = 40) private String nationalId;
    @Size(max = 160) private String emergencyName;
    @Size(max = 32) private String emergencyPhone;
}
