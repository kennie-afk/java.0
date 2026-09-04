package com.kenyarealestate.notification.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreferenceResponse {
    private String category;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean inAppEnabled;
}
