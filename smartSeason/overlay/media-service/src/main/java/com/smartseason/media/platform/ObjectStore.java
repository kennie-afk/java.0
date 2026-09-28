package com.smartseason.media.platform;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Where the bytes actually go.
 *
 * <p>Until this existed, media-service kept {@code MediaAsset}, {@code UploadTicket} and
 * {@code MediaVariant} rows and told callers through its API description that it offered
 * pre-signed uploads — with no S3 client on the classpath at all. It was metadata about
 * files that were never stored anywhere.
 *
 * <h2>Why pre-signed URLs rather than proxying the upload</h2>
 * A client uploads straight to the object store and this service never sees the bytes. The
 * alternative — streaming uploads through the service — makes every large file a long-lived
 * request holding a container thread, a connection through the gateway and heap for the
 * buffer, so a handful of concurrent uploads of farm photography can stall a service whose
 * real job is bookkeeping. Pre-signing keeps the transfer off the critical path entirely,
 * which is the only version of this that survives being popular.
 *
 * <h2>Why MinIO, and why that is not a lock-in</h2>
 * The endpoint is S3-compatible and configured, not hard-coded. Pointing {@code S3_ENDPOINT}
 * at AWS S3, DigitalOcean Spaces or Cloudflare R2 later is a ConfigMap change; nothing in
 * this class knows which it is talking to. Path-style access is forced because MinIO and
 * most self-hosted gateways do not do virtual-host-style buckets.
 *
 * <h2>Disabled by default is deliberate</h2>
 * With no {@code S3_ENDPOINT} the bean still exists but reports itself unavailable, and
 * callers are expected to check {@link #isEnabled()}. Failing at start-up would make a
 * local run of 27 services depend on an object store that most of them have nothing to do
 * with; failing at first upload with a clear message is the better trade.
 */
@Component
public class ObjectStore implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ObjectStore.class);

    private final String endpoint;
    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final Duration ttl;

    private S3Client client;
    private S3Presigner presigner;
    private volatile boolean enabled;

    public ObjectStore(
            @Value("${smartseason.s3.endpoint:${S3_ENDPOINT:}}") String endpoint,
            @Value("${smartseason.s3.bucket:${S3_BUCKET:smartseason-media}}") String bucket,
            @Value("${smartseason.s3.region:${S3_REGION:us-east-1}}") String region,
            @Value("${smartseason.s3.access-key:${S3_ACCESS_KEY:}}") String accessKey,
            @Value("${smartseason.s3.secret-key:${S3_SECRET_KEY:}}") String secretKey,
            @Value("${smartseason.s3.url-ttl-minutes:15}") long ttlMinutes) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(endpoint)) {
            log.info("Object storage is not configured (S3_ENDPOINT unset); uploads will be refused.");
            return;
        }
        try {
            var credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));

            this.client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    // MinIO and most self-hosted gateways serve buckets as a path segment,
                    // not as a subdomain. Without this the SDK builds a hostname that does
                    // not resolve and the failure reads like DNS rather than configuration.
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();

            this.presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .credentialsProvider(credentials)
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();

            ensureBucket();
            this.enabled = true;
            log.info("Object storage ready: bucket '{}' at {}", bucket, endpoint);
        } catch (Exception ex) {
            // Not fatal. A media service that will not start takes 26 unrelated services'
            // worth of deployment with it when the only broken thing is a bucket.
            log.warn("Object storage unavailable at {}: {}", endpoint, ex.getMessage());
        }
    }

    /** Creates the bucket if it is missing, so a fresh cluster needs no manual step. */
    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created bucket '{}'", bucket);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * A URL the client can PUT to directly, valid for a short window.
     *
     * <p>Short on purpose: a pre-signed URL is a bearer credential for one object, and
     * anything long-lived is one screenshot away from being someone else's upload slot.
     */
    public String presignUpload(String key, String contentType) {
        requireEnabled();
        var put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        return presigner.presignPutObject(
                        PutObjectPresignRequest.builder()
                                .signatureDuration(ttl)
                                .putObjectRequest(put)
                                .build())
                .url()
                .toString();
    }

    /** A URL the client can GET the object from, valid for the same short window. */
    public String presignDownload(String key) {
        requireEnabled();
        var get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(ttl)
                                .getObjectRequest(get)
                                .build())
                .url()
                .toString();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Object storage is not configured. Set S3_ENDPOINT, S3_ACCESS_KEY and "
                            + "S3_SECRET_KEY; in Kubernetes these are already wired to the "
                            + "objectstore Service and smartseason-secrets.");
        }
    }
}
