package com.smartseason.identity.web;

import com.smartseason.identity.auth.AuthDtos.LoginRequest;
import com.smartseason.identity.auth.AuthDtos.ProfileResponse;
import com.smartseason.identity.auth.AuthDtos.RefreshRequest;
import com.smartseason.identity.auth.AuthDtos.RegisterRequest;
import com.smartseason.identity.auth.AuthDtos.TokenResponse;
import com.smartseason.identity.auth.AuthService;
import com.smartseason.identity.platform.DomainRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token refresh and profile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register an organisation and its first administrator")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest http) {
        TokenResponse response = authService.register(request, userAgent(http), clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access and refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, userAgent(http), clientIp(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new access token")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return authService.refresh(request, userAgent(http), clientIp(http));
    }

    @GetMapping("/me")
    @Operation(summary = "Return the authenticated caller's profile")
    public ProfileResponse me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new DomainRuleException("No authenticated caller");
        }
        return authService.profile(UUID.fromString(authentication.getName()));
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
