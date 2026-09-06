package com.soko.security;

import java.util.UUID;

public record Principal(UUID userId, UUID tenantId, String email, String role) {}
