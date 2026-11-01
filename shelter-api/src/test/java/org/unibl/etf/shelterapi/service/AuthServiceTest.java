package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unibl.etf.shelterapi.dto.AuthResponse;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.exception.EmailAlreadyUsedException;
import org.unibl.etf.shelterapi.exception.InvalidCredentialsException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private static User ana() {
        return new User("ana@example.com", "hashed", "Ana", "Anic", Role.USER);
    }

    @Test
    void registerStoresNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret-pass-1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("  Ana@Example.com ", "secret-pass-1", " Ana ", " Anic "));

        assertEquals("ana@example.com", response.email());
        assertEquals("Ana", response.firstName());
        assertEquals(Role.USER, response.role());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", captor.getValue().getPasswordHash());
    }

    @Test
    void registerRejectsEmailThatIsAlreadyUsed() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("ana@example.com", "secret-pass-1", "Ana", "Anic");

        assertThrows(EmailAlreadyUsedException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = ana();
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret-pass-1", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("token-value");
        when(jwtService.getExpiresInSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("ANA@example.com", "secret-pass-1"));

        assertEquals("token-value", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals("USER", response.role());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana()));
        when(passwordEncoder.matches("wrong-pass-1", "hashed")).thenReturn(false);
        LoginRequest request = new LoginRequest("ana@example.com", "wrong-pass-1");

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest("nobody@example.com", "secret-pass-1");

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void currentUserIsReturnedByEmail() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana()));

        UserResponse response = authService.getCurrentUser("ana@example.com");

        assertEquals("Ana", response.firstName());
        assertEquals(Role.USER, response.role());
    }

    @Test
    void currentUserThrowsWhenMissing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser("nobody@example.com"));
    }
}
