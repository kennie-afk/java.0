package com.hms.platform.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * hms.storage.mode = local (files on this machine's disk) or s3 (any S3-compatible store). Anything else stops the service at start-up
 * rather than silently storing nowhere.
 */
@Configuration
class StorageConfig {

    /** The link-signing key is derived from the JWT secret, so there is one secret to manage and it is never the token key itself. */
    @Bean
    ObjectLinks objectLinks(@Value("${hms.jwt.secret}") String jwtSecret) throws NoSuchAlgorithmException {
        return new ObjectLinks(MessageDigest.getInstance("SHA-256").digest(("hms-object-links|" + jwtSecret).getBytes(StandardCharsets.UTF_8)));
    }

    @Bean
    ObjectStore objectStore(ObjectLinks links, @Value("${hms.storage.mode:local}") String mode, @Value("${hms.storage.local-path:./data/objects}") String path,
                            @Value("${hms.jwt.secret}") String jwtSecret,
                            @Value("${hms.storage.s3.endpoint:}") String endpoint, @Value("${hms.storage.s3.public-endpoint:}") String publicEndpoint,
                            @Value("${hms.storage.s3.region:us-east-1}") String region, @Value("${hms.storage.s3.bucket:}") String bucket,
                            @Value("${hms.storage.s3.access-key:}") String accessKey, @Value("${hms.storage.s3.secret-key:}") String secretKey,
                            @Value("${hms.storage.s3.create-bucket:false}") boolean createBucket)
            throws NoSuchAlgorithmException {
        if ("s3".equalsIgnoreCase(mode)) {
            for (String[] required : new String[][] {{"HMS_STORAGE_S3_ENDPOINT", endpoint}, {"HMS_STORAGE_S3_BUCKET", bucket},
                    {"HMS_STORAGE_S3_ACCESS_KEY", accessKey}, {"HMS_STORAGE_S3_SECRET_KEY", secretKey}}) {
                if (required[1] == null || required[1].isBlank()) {
                    throw new IllegalStateException("HMS_STORAGE_MODE=s3 needs " + required[0]);
                }
            }
            S3ObjectStore store = new S3ObjectStore(new S3ObjectStore.Config(endpoint, publicEndpoint, region, bucket, accessKey, secretKey), links);
            if (createBucket) {
                store.ensureBucket();
            }
            return store;
        }
        if (!"local".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("HMS_STORAGE_MODE=" + mode + " is not known. Use 'local' or 's3'.");
        }
        byte[] key = MessageDigest.getInstance("SHA-256").digest(("hms-object-links|" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        return new LocalObjectStore(Path.of(path), key);
    }
}
