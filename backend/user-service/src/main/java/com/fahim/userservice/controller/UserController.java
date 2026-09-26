package com.fahim.userservice.controller;

import com.fahim.userservice.dto.ProfileResponse;
import com.fahim.userservice.dto.RegisterRequest;
import com.fahim.userservice.dto.UpdateProfileRequest;
import com.fahim.userservice.model.Profile;
import com.fahim.userservice.service.ProfileService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile endpoints are {@code /users/me} only — the caller's identity comes from the token's
 * {@code sub}, never from a path variable, so there is no id for a client to tamper with.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final ProfileService profileService;

    public UserController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/register")
    public ResponseEntity<ProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        Profile profile = profileService.register(request);
        return ResponseEntity.created(URI.create("/users/me")).body(ProfileResponse.from(profile));
    }

    @GetMapping("/me")
    public ProfileResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(profileService.getBySub(jwt.getSubject()));
    }

    @PutMapping("/me")
    public ProfileResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(profileService.update(jwt.getSubject(), request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyProfile(@AuthenticationPrincipal Jwt jwt) {
        profileService.delete(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
