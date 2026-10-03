package com.hms.platform.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** hms.storage.mode = local (the only one built). Anything else stops the service at start-up rather than silently storing nowhere. */
@Configuration
class StorageConfig {

    @Bean
    ObjectStore objectStore(@Value("${hms.storage.mode:local}") String mode, @Value("${hms.storage.local-path:./data/objects}") String path,
                            @Value("${hms.jwt.secret}") String jwtSecret) throws NoSuchAlgorithmException {
        if (!"local".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("HMS_STORAGE_MODE=" + mode + " is not implemented. Only 'local' exists; an S3-compatible store has not been built.");
        }
        // The link-signing key is derived from the JWT secret, so there is one secret to manage and it is never the token key itself.
        byte[] key = MessageDigest.getInstance("SHA-256").digest(("hms-object-links|" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        return new LocalObjectStore(Path.of(path), key);
    }
}
