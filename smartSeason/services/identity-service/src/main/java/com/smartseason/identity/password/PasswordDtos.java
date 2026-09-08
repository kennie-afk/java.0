package com.smartseason.identity.password;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PasswordDtos {

    private PasswordDtos() {
    }

    /** Changing your own password: proving the current one is what authorises it. */
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 200) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    /**
     * The response never says whether the address is registered. Telling an
     * anonymous caller "no such account" turns this endpoint into a way to
     * enumerate who has one.
     */
    public record ForgotPasswordResponse(String message, String devCode) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank @Size(min = 12, max = 200) String newPassword) {
    }

    public record SimpleResponse(String message) {
    }
}
