package com.kenyarealestate.user.dto;

import lombok.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserContactResponse {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
}
