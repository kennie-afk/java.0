package com.hms.portal;

import static com.hms.portal.PortalModels.*;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal/auth")
class PortalAuthController {

    private final PortalAuthService auth;

    PortalAuthController(PortalAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/activate")
    Map<String, String> activate(@Valid @RequestBody ActivateInput in) {
        auth.activate(in);
        return Map.of("status", "activated");
    }

    @PostMapping("/login")
    Session login(@Valid @RequestBody LoginInput in) {
        return auth.login(in);
    }
}
