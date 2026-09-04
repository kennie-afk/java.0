package com.kenyarealestate.notification.dto;

import lombok.*;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class UserContactResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
}
