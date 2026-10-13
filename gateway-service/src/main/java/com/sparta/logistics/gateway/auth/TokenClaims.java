package com.sparta.logistics.gateway.auth;

import java.util.UUID;

public record TokenClaims(UUID userId, String username, String role) {
}
