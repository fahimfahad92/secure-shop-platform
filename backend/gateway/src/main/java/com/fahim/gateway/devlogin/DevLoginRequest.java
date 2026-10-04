package com.fahim.gateway.devlogin;

import jakarta.validation.constraints.NotBlank;

record DevLoginRequest(@NotBlank String username, @NotBlank String password) {

    /** Keeps the password out of logs if the request is ever printed. */
    @Override
    public String toString() {
        return "DevLoginRequest[username=" + username + ", password=***]";
    }
}
