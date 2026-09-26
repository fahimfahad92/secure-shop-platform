package com.fahim.userservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fahim.userservice.client.KeycloakAdminClient;
import com.fahim.userservice.dto.RegisterRequest;
import com.fahim.userservice.repository.ProfileRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserControllerSecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ProfileRepository profileRepository;

    @MockitoBean private KeycloakAdminClient keycloakAdminClient;

    private static final String REGISTER_JSON =
            """
            {
              "username": "newuser",
              "email": "newuser@example.com",
              "password": "correct-horse",
              "fullName": "New User",
              "address": "12 Example Road",
              "phone": "0123456789"
            }
            """;

    @BeforeEach
    void reset() {
        profileRepository.deleteAll();
    }

    @Test
    void register_withoutToken_succeeds() throws Exception {
        String sub = UUID.randomUUID().toString();
        given(keycloakAdminClient.createUser(any(RegisterRequest.class))).willReturn(sub);

        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakSub").value(sub))
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void register_invalidPayload_returns400() throws Exception {
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"a\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyProfile_noToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfile_withToken_returnsTheCallersOwnProfile() throws Exception {
        String sub = UUID.randomUUID().toString();
        given(keycloakAdminClient.createUser(any(RegisterRequest.class))).willReturn(sub);
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/users/me").with(jwt().jwt(token -> token.subject(sub))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloakSub").value(sub))
                .andExpect(jsonPath("$.address").value("12 Example Road"));
    }

    @Test
    void getMyProfile_tokenWithNoProfile_returns404() throws Exception {
        mockMvc.perform(
                        get("/users/me")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.subject(
                                                                        UUID.randomUUID()
                                                                                .toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMyProfile_changesOnlyProfileFields() throws Exception {
        String sub = UUID.randomUUID().toString();
        given(keycloakAdminClient.createUser(any(RegisterRequest.class))).willReturn(sub);
        mockMvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REGISTER_JSON))
                .andExpect(status().isCreated());

        // username and email are not settable here; anything sent for them is ignored
        mockMvc.perform(
                        put("/users/me")
                                .with(jwt().jwt(token -> token.subject(sub)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "fullName": "Renamed User",
                                          "address": "99 Other Street",
                                          "phone": "9876543210",
                                          "username": "hacker",
                                          "email": "hacker@example.com"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed User"))
                .andExpect(jsonPath("$.address").value("99 Other Street"))
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    void updateMyProfile_noToken_returns401() throws Exception {
        mockMvc.perform(
                        put("/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}
