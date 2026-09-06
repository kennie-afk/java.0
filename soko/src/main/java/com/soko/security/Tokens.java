package com.soko.security;

import io.jsonwebtoken.Claims;
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
public class Tokens {

    private final SecretKey key;
    private final Duration ttl;

    public Tokens(
            @Value("${soko.jwt.secret}") String secret,
            @Value("${soko.jwt.ttl-minutes:720}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(Principal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.userId().toString())
                .claim("tid", principal.tenantId().toString())
                .claim("email", principal.email())
                .claim("role", principal.role())
                .claim("sid", principal.supplierId() == null ? null : principal.supplierId().toString())
                .claim("cid", principal.customerId() == null ? null : principal.customerId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Principal verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String supplier = claims.get("sid", String.class);
        String customer = claims.get("cid", String.class);
        return new Principal(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("tid", String.class)),
                claims.get("email", String.class),
                claims.get("role", String.class),
                supplier == null ? null : UUID.fromString(supplier),
                customer == null ? null : UUID.fromString(customer));
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }
}
