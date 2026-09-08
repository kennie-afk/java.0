package com.smartseason.identity.web;

import com.smartseason.identity.password.PasswordDtos.ChangePasswordRequest;
import com.smartseason.identity.password.PasswordDtos.ForgotPasswordRequest;
import com.smartseason.identity.password.PasswordDtos.ForgotPasswordResponse;
import com.smartseason.identity.password.PasswordDtos.ResetPasswordRequest;
import com.smartseason.identity.password.PasswordDtos.SimpleResponse;
import com.smartseason.identity.password.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Note the two base paths. Everything under `/auth/**` is permitAll in
 * SecurityConfig because sign-in has to be reachable without a token, so
 * changing a password - which must be authenticated - deliberately lives under
 * `/account/**` instead.
 */
@RestController
@Tag(name = "Password", description = "Changing and resetting passwords")
public class PasswordController {

    private final PasswordService service;

    public PasswordController(PasswordService service) {
        this.service = service;
    }

    @PostMapping("/api/identity/v1/account/change-password")
    @Operation(summary = "Change your own password, proving the current one")
    public SimpleResponse change(@Valid @RequestBody ChangePasswordRequest request,
                                 Authentication authentication) {
        service.changePassword(UUID.fromString(authentication.getName()), request);
        return new SimpleResponse("Your password has been changed. Other sessions were signed out.");
    }

    @PostMapping("/api/identity/v1/auth/forgot-password")
    @Operation(summary = "Request a reset code; the reply is the same either way")
    public ForgotPasswordResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        return service.requestReset(request.email());
    }

    @PostMapping("/api/identity/v1/auth/reset-password")
    @Operation(summary = "Set a new password using a reset code")
    public SimpleResponse reset(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request);
        return new SimpleResponse("Your password has been reset. Sign in with the new one.");
    }
}
