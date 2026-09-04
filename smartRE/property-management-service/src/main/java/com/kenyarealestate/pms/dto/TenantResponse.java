package com.kenyarealestate.pms.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TenantResponse {
    private UUID id;
    private UUID landlordId;
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private String nationalId;
    private String emergencyName;
    private String emergencyPhone;
    private boolean hasActiveLease;
    private LocalDateTime createdAt;
}
