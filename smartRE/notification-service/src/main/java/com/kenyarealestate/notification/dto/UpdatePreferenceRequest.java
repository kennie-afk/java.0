package com.kenyarealestate.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePreferenceRequest {
    @NotBlank
    private String category;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean inAppEnabled;
}
