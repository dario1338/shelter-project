package org.unibl.etf.shelterapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.dto.AuthResponse;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.EmailAlreadyUsedException;
import org.unibl.etf.shelterapi.exception.InvalidCredentialsException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.service.AuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String VALID_REGISTRATION =
            "{\"email\":\"ana@example.com\",\"password\":\"secret-pass-1\",\"firstName\":\"Ana\",\"lastName\":\"Anic\"}";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void registerReturnsCreatedUser() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(1L, "ana@example.com", "Ana", "Anic", Role.USER));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret-pass-1\","
                                + "\"firstName\":\"Ana\",\"lastName\":\"Anic\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"short\","
                                + "\"firstName\":\"Ana\",\"lastName\":\"Anic\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturnsConflictWhenEmailIsTaken() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("token-value", "Bearer", 3600L, "USER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"wrong-pass-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
