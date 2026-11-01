package org.unibl.etf.shelterapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.unibl.etf.shelterapi.config.AdminProperties;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminInitializer initializer(String email, String secret) {
        return new AdminInitializer(new AdminProperties(email, secret), userRepository, passwordEncoder);
    }

    @Test
    void skipsWhenEmailIsMissing() {
        initializer(null, "some-value").run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void skipsWhenPasswordIsBlank() {
        initializer("admin@shelter.test", "  ").run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(userRepository.existsByEmail("admin@shelter.test")).thenReturn(true);

        initializer("admin@shelter.test", "some-value").run(null);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createsAdminWithHashedPassword() {
        when(userRepository.existsByEmail("admin@shelter.test")).thenReturn(false);
        when(passwordEncoder.encode("some-value")).thenReturn("hashed");

        initializer(" Admin@Shelter.Test ", "some-value").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("admin@shelter.test", captor.getValue().getEmail());
        assertEquals("hashed", captor.getValue().getPasswordHash());
        assertEquals(Role.ADMIN, captor.getValue().getRole());
    }
}
