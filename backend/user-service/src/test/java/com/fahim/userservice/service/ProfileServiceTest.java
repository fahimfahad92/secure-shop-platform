package com.fahim.userservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fahim.userservice.client.KeycloakAdminClient;
import com.fahim.userservice.dto.RegisterRequest;
import com.fahim.userservice.dto.UpdateProfileRequest;
import com.fahim.userservice.exception.KeycloakAdminException;
import com.fahim.userservice.exception.ProfileNotFoundException;
import com.fahim.userservice.model.Profile;
import com.fahim.userservice.repository.ProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final String SUB = "7663bcb4-5f41-4b73-a47c-534a052c5a93";

    @Mock private ProfileRepository profileRepository;

    @Mock private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks private ProfileService profileService;

    private static RegisterRequest registerRequest() {
        return new RegisterRequest(
                "newuser", "newuser@example.com", "correct-horse", "New User", "12 Road", "0123");
    }

    @Test
    void register_createsKeycloakUserThenProfileKeyedByItsSub() {
        when(keycloakAdminClient.createUser(any(RegisterRequest.class))).thenReturn(SUB);
        when(profileRepository.save(any(Profile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Profile result = profileService.register(registerRequest());

        assertThat(result.getKeycloakSub()).isEqualTo(SUB);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
        assertThat(result.getAddress()).isEqualTo("12 Road");
        verify(keycloakAdminClient, never()).deleteUser(any());
    }

    @Test
    void register_profileWriteFails_deletesTheKeycloakUserAgainAndRethrows() {
        when(keycloakAdminClient.createUser(any(RegisterRequest.class))).thenReturn(SUB);
        when(profileRepository.save(any(Profile.class)))
                .thenThrow(new IllegalStateException("constraint violation"));

        assertThatThrownBy(() -> profileService.register(registerRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("constraint violation");

        verify(keycloakAdminClient).deleteUser(SUB);
    }

    @Test
    void register_compensationAlsoFails_stillRethrowsTheOriginalFailure() {
        when(keycloakAdminClient.createUser(any(RegisterRequest.class))).thenReturn(SUB);
        when(profileRepository.save(any(Profile.class)))
                .thenThrow(new IllegalStateException("constraint violation"));
        org.mockito.Mockito.doThrow(new KeycloakAdminException("Keycloak unreachable"))
                .when(keycloakAdminClient)
                .deleteUser(SUB);

        // The caller sees why registration failed, not why cleanup failed; the orphaned
        // Keycloak user is reported through the error log instead.
        assertThatThrownBy(() -> profileService.register(registerRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("constraint violation");
    }

    @Test
    void register_keycloakCreateFails_writesNoProfile() {
        when(keycloakAdminClient.createUser(any(RegisterRequest.class)))
                .thenThrow(new KeycloakAdminException("Keycloak unreachable"));

        assertThatThrownBy(() -> profileService.register(registerRequest()))
                .isInstanceOf(KeycloakAdminException.class);

        verify(profileRepository, never()).save(any(Profile.class));
        verify(keycloakAdminClient, never()).deleteUser(any());
    }

    @Test
    void getBySub_found_returnsProfile() {
        Profile profile = new Profile();
        profile.setKeycloakSub(SUB);
        when(profileRepository.findById(SUB)).thenReturn(Optional.of(profile));

        assertThat(profileService.getBySub(SUB).getKeycloakSub()).isEqualTo(SUB);
    }

    @Test
    void getBySub_notFound_throwsProfileNotFoundException() {
        when(profileRepository.findById(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getBySub(SUB))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessageContaining(SUB);
    }

    @Test
    void update_changesProfileFieldsOnly() {
        Profile existing = new Profile();
        existing.setKeycloakSub(SUB);
        existing.setUsername("newuser");
        existing.setEmail("newuser@example.com");
        when(profileRepository.findById(SUB)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any(Profile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Profile result =
                profileService.update(
                        SUB, new UpdateProfileRequest("Renamed", "99 Other Street", "9876"));

        assertThat(result.getFullName()).isEqualTo("Renamed");
        assertThat(result.getAddress()).isEqualTo("99 Other Street");
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
    }

    @Test
    void delete_removesProfileThenKeycloakUser() {
        Profile existing = new Profile();
        existing.setKeycloakSub(SUB);
        when(profileRepository.findById(SUB)).thenReturn(Optional.of(existing));

        profileService.delete(SUB);

        verify(profileRepository).delete(existing);
        verify(keycloakAdminClient).deleteUser(SUB);
    }

    @Test
    void delete_noProfile_doesNotTouchKeycloak() {
        when(profileRepository.findById(SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.delete(SUB))
                .isInstanceOf(ProfileNotFoundException.class);

        verify(keycloakAdminClient, never()).deleteUser(any());
    }
}
