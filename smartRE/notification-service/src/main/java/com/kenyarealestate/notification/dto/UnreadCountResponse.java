package com.kenyarealestate.notification.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UnreadCountResponse {
    private long unread;
}
