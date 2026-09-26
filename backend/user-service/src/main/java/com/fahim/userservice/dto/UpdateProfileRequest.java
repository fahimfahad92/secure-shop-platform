package com.fahim.userservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Deliberately excludes username, email and password. Those are Keycloak's to change, and accepting
 * them here would let a client edit its own identity through a profile update.
 */
public record UpdateProfileRequest(
        @Size(max = 200) String fullName,
        @Size(max = 500) String address,
        @Size(max = 30) String phone) {}
