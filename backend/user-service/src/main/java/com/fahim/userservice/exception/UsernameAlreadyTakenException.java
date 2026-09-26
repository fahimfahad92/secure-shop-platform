package com.fahim.userservice.exception;

public class UsernameAlreadyTakenException extends RuntimeException {

    public UsernameAlreadyTakenException(String username) {
        super("Username or email already registered: " + username);
    }
}
