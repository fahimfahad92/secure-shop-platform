package com.fahim.userservice.service;

import com.fahim.userservice.client.KeycloakAdminClient;
import com.fahim.userservice.dto.RegisterRequest;
import com.fahim.userservice.dto.UpdateProfileRequest;
import com.fahim.userservice.exception.ProfileNotFoundException;
import com.fahim.userservice.model.Profile;
import com.fahim.userservice.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    public ProfileService(
            ProfileRepository profileRepository, KeycloakAdminClient keycloakAdminClient) {
        this.profileRepository = profileRepository;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    /**
     * Creates the Keycloak user first, then the local profile row keyed by the returned {@code
     * sub}.
     *
     * <p>Two systems, no shared transaction. If the local write fails the Keycloak user is deleted
     * again, otherwise that username is stuck in Keycloak and the user gets a confusing "already
     * exists" on their next attempt. Deliberately <em>not</em> {@code @Transactional}: the Keycloak
     * call is not transactional, and wrapping the pair would only give the illusion that it is.
     */
    public Profile register(RegisterRequest request) {
        String keycloakSub = keycloakAdminClient.createUser(request);

        try {
            Profile profile = new Profile();
            profile.setKeycloakSub(keycloakSub);
            profile.setUsername(request.username());
            profile.setEmail(request.email());
            profile.setFullName(request.fullName());
            profile.setAddress(request.address());
            profile.setPhone(request.phone());
            return profileRepository.save(profile);
        } catch (RuntimeException profileWriteFailure) {
            compensate(keycloakSub, profileWriteFailure);
            throw profileWriteFailure;
        }
    }

    /**
     * Best-effort rollback of the Keycloak user. If this also fails the user is orphaned in
     * Keycloak and needs manual cleanup — logged loudly because nothing else will notice. Closing
     * this window properly is what the Outbox stretch phase is for.
     */
    private void compensate(String keycloakSub, RuntimeException cause) {
        try {
            keycloakAdminClient.deleteUser(keycloakSub);
            log.warn(
                    "Profile write failed for Keycloak user {}; the Keycloak user was deleted again",
                    keycloakSub,
                    cause);
        } catch (RuntimeException compensationFailure) {
            log.error(
                    "ORPHANED KEYCLOAK USER {} — profile write failed and the compensating delete"
                            + " also failed. Manual cleanup required.",
                    keycloakSub,
                    compensationFailure);
        }
    }

    @Transactional(readOnly = true)
    public Profile getBySub(String keycloakSub) {
        return profileRepository
                .findById(keycloakSub)
                .orElseThrow(() -> new ProfileNotFoundException(keycloakSub));
    }

    @Transactional
    public Profile update(String keycloakSub, UpdateProfileRequest request) {
        Profile profile = getBySub(keycloakSub);
        profile.setFullName(request.fullName());
        profile.setAddress(request.address());
        profile.setPhone(request.phone());
        return profileRepository.save(profile);
    }

    /**
     * Removes the profile row, then the Keycloak user. Same order of reasoning as registration in
     * reverse: the local row goes first because it is the one we can roll back by simply failing.
     */
    public void delete(String keycloakSub) {
        Profile profile = getBySub(keycloakSub);
        profileRepository.delete(profile);
        keycloakAdminClient.deleteUser(keycloakSub);
    }
}
