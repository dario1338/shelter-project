package org.unibl.etf.shelterapi.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.unibl.etf.shelterapi.config.CsrfProperties;
import org.unibl.etf.shelterapi.config.RefreshTokenProperties;
import org.unibl.etf.shelterapi.dto.AuthResponse;
import org.unibl.etf.shelterapi.dto.ChangePasswordRequest;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.exception.ApiExceptionHandler;
import org.unibl.etf.shelterapi.exception.EmailAlreadyUsedException;
import org.unibl.etf.shelterapi.exception.InvalidCredentialsException;
import org.unibl.etf.shelterapi.exception.InvalidPasswordException;
import org.unibl.etf.shelterapi.exception.InvalidRefreshTokenException;
import org.unibl.etf.shelterapi.exception.TooManyAttemptsException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.service.AuthService;
import org.unibl.etf.shelterapi.service.OriginValidator;
import org.unibl.etf.shelterapi.service.RefreshCookieFactory;
import org.unibl.etf.shelterapi.service.SessionService;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String ORIGIN = "https://app.shelter.test";
    private static final String EMAIL = "ana@example.com";
    private static final String COOKIE = RefreshCookieFactory.COOKIE_NAME;
    private static final String REGISTER = "/api/auth/register";
    private static final String LOGIN = "/api/auth/login";
    private static final String REFRESH = "/api/auth/refresh";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String CHANGE_PASSWORD = "/api/auth/change-password";
    private static final String LOGIN_BODY = "{\"email\":\"ana@example.com\",\"password\":\"strong-pass-1\"}";

    private final Principal ana = new TestingAuthenticationToken(EMAIL, null);

    @Mock
    private AuthService authService;

    @Mock
    private SessionService sessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OriginValidator originValidator = new OriginValidator(new CsrfProperties(List.of(ORIGIN)));
        RefreshCookieFactory cookies = new RefreshCookieFactory(new RefreshTokenProperties(Duration.ofDays(7), true));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, sessionService, originValidator, cookies))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static SessionService.Session session(String refreshToken) {
        return new SessionService.Session(new AuthResponse("access-token", "Bearer", 900, "USER"), refreshToken);
    }

    @Test
    void registerReturnsTheCreatedUser() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserResponse(1L, EMAIL, "Ana", "Anic", Role.USER));

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"strong-pass-1\","
                                + "\"firstName\":\"Ana\",\"lastName\":\"Anic\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void registerRejectsAnInvalidBody() throws Exception {
        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"firstName\":\"\",\"lastName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReportsAnEmailThatIsAlreadyUsed() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\",\"password\":\"strong-pass-1\","
                                + "\"firstName\":\"Ana\",\"lastName\":\"Anic\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTheAccessTokenAndSetsAHardenedRefreshCookie() throws Exception {
        when(sessionService.login(any(LoginRequest.class))).thenReturn(session("refresh-value"));

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE + "=refresh-value"),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api/auth"))));
    }

    @Test
    void loginRejectsWrongCredentials() throws Exception {
        when(sessionService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsRateLimitedWithRetryAfter() throws Exception {
        when(sessionService.login(any(LoginRequest.class))).thenThrow(new TooManyAttemptsException(120));

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"));
    }

    @Test
    void refreshRotatesTheCookie() throws Exception {
        when(sessionService.refresh("old-token")).thenReturn(session("new-token"));

        mockMvc.perform(post(REFRESH)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(COOKIE, "old-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE + "=new-token")));
    }

    @Test
    void refreshWithoutACookieIsUnauthorized() throws Exception {
        when(sessionService.refresh(null)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post(REFRESH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsAForeignOrMissingOrigin() throws Exception {
        mockMvc.perform(post(REFRESH)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(COOKIE, "old-token")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(REFRESH).cookie(new Cookie(COOKIE, "old-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sessionService);
    }

    @Test
    void logoutRevokesTheTokenAndClearsTheCookie() throws Exception {
        mockMvc.perform(post(LOGOUT)
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(COOKIE, "old-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(sessionService).logout("old-token");
    }

    @Test
    void logoutRejectsAForeignOrigin() throws Exception {
        mockMvc.perform(post(LOGOUT)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(COOKIE, "old-token")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sessionService);
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        when(authService.getCurrentUser(EMAIL)).thenReturn(new UserResponse(1L, EMAIL, "Ana", "Anic", Role.USER));

        mockMvc.perform(get("/api/auth/me").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void changePasswordReturnsNoContent() throws Exception {
        mockMvc.perform(post(CHANGE_PASSWORD)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"strong-pass-1\",\"newPassword\":\"brand-new-pass-2\"}"))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(eq(EMAIL), any(ChangePasswordRequest.class));
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() throws Exception {
        doThrow(new InvalidPasswordException()).when(authService)
                .changePassword(eq(EMAIL), any(ChangePasswordRequest.class));

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-pass-0\",\"newPassword\":\"brand-new-pass-2\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordRejectsAShortNewPassword() throws Exception {
        mockMvc.perform(post(CHANGE_PASSWORD)
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"strong-pass-1\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }
}