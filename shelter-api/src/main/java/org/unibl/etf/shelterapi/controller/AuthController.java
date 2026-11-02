package org.unibl.etf.shelterapi.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unibl.etf.shelterapi.dto.AuthResponse;
import org.unibl.etf.shelterapi.dto.ChangePasswordRequest;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.service.AuthService;
import org.unibl.etf.shelterapi.service.OriginValidator;
import org.unibl.etf.shelterapi.service.RefreshCookieFactory;
import org.unibl.etf.shelterapi.service.SessionService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final OriginValidator originValidator;
    private final RefreshCookieFactory refreshCookies;

    public AuthController(AuthService authService,
                          SessionService sessionService,
                          OriginValidator originValidator,
                          RefreshCookieFactory refreshCookies) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.originValidator = originValidator;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(sessionService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        originValidator.requireAllowed(origin);
        return respond(sessionService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        originValidator.requireAllowed(origin);
        sessionService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> respond(SessionService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.create(session.refreshToken()).toString())
                .body(session.auth());
    }
}