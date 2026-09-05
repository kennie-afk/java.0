package com.smartseason.fraud.platform;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " " + id + " not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
