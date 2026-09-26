package com.fahim.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The password is forwarded to Keycloak and never stored or logged here — Keycloak owns
 * credentials, this service owns profile data.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Size(max = 200) String fullName,
        @Size(max = 500) String address,
        @Size(max = 30) String phone) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
    }
}
