package com.smartseason.identity.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public TokenService(@Value("${smartseason.jwt.secret}") String secret,
                        @Value("${smartseason.jwt.issuer}") String issuer,
                        @Value("${smartseason.jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException(
                    "smartseason.jwt.secret must be at least 64 bytes for HS512-grade signing");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    public String mintAccessToken(UUID userId, UUID tenantId, String roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
