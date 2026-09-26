package com.fahim.userservice.dto;

import com.fahim.userservice.model.Profile;
import java.time.Instant;

public record ProfileResponse(
        String keycloakSub,
        String username,
        String email,
        String fullName,
        String address,
        String phone,
        Instant createdAt,
        Instant updatedAt) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(
                profile.getKeycloakSub(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getFullName(),
                profile.getAddress(),
                profile.getPhone(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
