package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.unibl.etf.shelterapi.dto.ChangePasswordRequest;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.exception.EmailAlreadyUsedException;
import org.unibl.etf.shelterapi.exception.InvalidCredentialsException;
import org.unibl.etf.shelterapi.exception.InvalidPasswordException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.exception.TooManyAttemptsException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String PASSWORD = "strong-pass-1";
    private static final String NEW_PASSWORD = "brand-new-pass-2";
    private static final String HASH = "hashed";
    private static final String DUMMY_HASH = "dummy-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginThrottleService loginThrottleService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, passwordEncoder, loginThrottleService, refreshTokenService);
    }

    private User existingUser() {
        User user = new User(EMAIL, HASH, "Ana", "Anic", Role.USER);
        ReflectionTestUtils.setField(user, "id", 5L);
        return user;
    }

    @Test
    void registerSavesANormalizedUserWithAHashedPassword() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest("  Ana@Example.com ", PASSWORD, " Ana ", " Anic "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(EMAIL, captor.getValue().getEmail());
        assertEquals(HASH, captor.getValue().getPasswordHash());
        assertEquals("Ana", captor.getValue().getFirstName());
        assertEquals(Role.USER, response.role());
    }

    @Test
    void registerRejectsAnEmailThatIsAlreadyUsed() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(new RegisterRequest(EMAIL, PASSWORD, "Ana", "Anic")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateReturnsTheUserAndResetsTheFailureCounter() {
        User user = existingUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        User result = authService.authenticate(new LoginRequest(" ANA@example.com ", PASSWORD));

        assertSame(user, result);
        verify(loginThrottleService).checkAllowed(EMAIL);
        verify(loginThrottleService).reset(EMAIL);
        verify(loginThrottleService, never()).recordFailure(EMAIL);
    }

    @Test
    void authenticateRecordsAFailureForAWrongPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.authenticate(new LoginRequest(EMAIL, PASSWORD)));
        verify(loginThrottleService).recordFailure(EMAIL);
        verify(loginThrottleService, never()).reset(EMAIL);
    }

    @Test
    void authenticateStillHashesWhenTheEmailDoesNotExist() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(PASSWORD, DUMMY_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.authenticate(new LoginRequest(EMAIL, PASSWORD)));
        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
        verify(loginThrottleService).recordFailure(EMAIL);
    }

    @Test
    void authenticateStopsWhenTheAccountIsLocked() {
        doThrow(new TooManyAttemptsException(60)).when(loginThrottleService).checkAllowed(EMAIL);

        assertThrows(TooManyAttemptsException.class,
                () -> authService.authenticate(new LoginRequest(EMAIL, PASSWORD)));
        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUserReturnsTheProfile() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

        UserResponse response = authService.getCurrentUser(EMAIL);

        assertEquals(EMAIL, response.email());
        assertEquals(5L, response.id());
    }

    @Test
    void getCurrentUserFailsForAnUnknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser(EMAIL));
    }

    @Test
    void changePasswordStoresTheNewHashAndRevokesAllRefreshTokens() {
        User user = existingUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hash");

        authService.changePassword(EMAIL, new ChangePasswordRequest(PASSWORD, NEW_PASSWORD));

        assertEquals("new-hash", user.getPasswordHash());
        verify(refreshTokenService).revokeAllForUser(5L);
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThrows(InvalidPasswordException.class,
                () -> authService.changePassword(EMAIL, new ChangePasswordRequest(PASSWORD, NEW_PASSWORD)));
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void changePasswordFailsForAnUnknownUser() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> authService.changePassword(EMAIL, new ChangePasswordRequest(PASSWORD, NEW_PASSWORD)));
    }
}