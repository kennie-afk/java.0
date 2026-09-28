package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** The token from a tenant invitation link. The user id is taken from the session, not here. */
@Data
public class RedeemInviteRequest {
    @NotBlank
    private String token;
}
