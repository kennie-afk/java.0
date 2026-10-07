package com.hms.platform.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Where uploaded files live. The application talks to this interface only: {@link LocalObjectStore} (one machine's disk) and
 * {@link S3ObjectStore} (any S3-compatible service, shared by every API pod) implement it.
 */
public interface ObjectStore {

    /** Keys are {@code <orgId>/<objectId>}; the store refuses anything else. */
    void put(String key, byte[] data);

    Optional<byte[]> read(String key);

    /** A link that lets the holder fetch the object, without signing in, until the time limit passes. */
    String presignGet(String key, String contentType, Duration ttl);

    /** True when the store keeps files on this machine's own disk, which is fine for a single node and not for a cluster. */
    boolean isLocal();
}
