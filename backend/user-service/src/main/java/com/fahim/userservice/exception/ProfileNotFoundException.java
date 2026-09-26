package com.fahim.userservice.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String keycloakSub) {
        super("Profile not found for subject: " + keycloakSub);
    }
}
