package com.kenyarealestate.pms.controller;

import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class CallerIdentity {

    private final JwtUtil jwtUtil;

    public CallerIdentity(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public UUID userId(HttpServletRequest request) {
        Object attr = request.getAttribute("authenticatedUserId");
        if (attr instanceof UUID uuid) return uuid;

        String token = extractToken(request);
        if (StringUtils.hasText(token) && jwtUtil.isValid(token)) {
            UUID uid = jwtUtil.extractUserId(token);
            if (uid != null) return uid;
        }
        throw new ForbiddenException("Unable to identify the signed-in user");
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("sre_token".equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
            }
        }
        String h = request.getHeader("Authorization");
        return (StringUtils.hasText(h) && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
