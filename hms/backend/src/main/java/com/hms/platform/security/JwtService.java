package com.hms.platform.security;

import com.hms.platform.config.HmsProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Signs and checks access tokens. A token says who you are, never what you may do. */
@Service
public class JwtService {

    public record Claims(UUID practitionerId, UUID orgId) {}

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(HmsProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(props.jwt().ttlMinutes());
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public String issue(UUID practitionerId, UUID orgId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(practitionerId.toString())
                .claim("org", orgId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            var body = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Claims(UUID.fromString(body.getSubject()), UUID.fromString(body.get("org", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
