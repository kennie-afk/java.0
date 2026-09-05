package com.kenyarealestate.property.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ImageHashService {

    @Value("${services.user-service-url}")
    private String userServiceUrl;

    @Value("${services.internal-secret}")
    private String internalSecret;

    @Value("${services.gateway-public-url:http://localhost:8080}")
    private String gatewayPublicUrl;

    @Value("${services.s3-public-base-url:https://smartre-documents.s3.amazonaws.com}")
    private String s3PublicBaseUrl;

    public Optional<String> computeSha256(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return Optional.empty();
        try {
            String fetchUrl = resolveFetchUrl(imageUrl);
            boolean rewrittenToTrustedInternalHost = !fetchUrl.equals(imageUrl);
            if (!rewrittenToTrustedInternalHost && !isAllowedHost(fetchUrl)) {
                log.warn("Refusing to fetch image from a non-allowlisted host for duplicate-photo check: {}", imageUrl);
                return Optional.empty();
            }

            URI uri = URI.create(fetchUrl);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            if (fetchUrl.contains("/api/documents/internal/files/")) {
                conn.setRequestProperty("X-Internal-Secret", internalSecret);
            }
            try (InputStream in = conn.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    md.update(buf, 0, read);
                }
                return Optional.of(java.util.HexFormat.of().formatHex(md.digest()));
            }
        } catch (Exception e) {
            log.warn("Could not fetch property image for duplicate check, skipping: {} ({})", imageUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private String resolveFetchUrl(String imageUrl) {
        int idx = imageUrl.indexOf("/api/documents/files/");
        if (idx == -1) return imageUrl;
        String suffix = imageUrl.substring(idx + "/api/documents/".length());
        return userServiceUrl + "/api/documents/internal/" + suffix;
    }

    boolean isAllowedHost(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && allowedHosts().contains(host.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private Set<String> allowedHosts() {
        Set<String> hosts = new HashSet<>();
        addHost(hosts, gatewayPublicUrl);
        addHost(hosts, s3PublicBaseUrl);
        return hosts;
    }

    private void addHost(Set<String> hosts, String url) {
        try {
            String host = URI.create(url).getHost();
            if (host != null) hosts.add(host.toLowerCase());
        } catch (Exception ignored) {
        }
    }
}
