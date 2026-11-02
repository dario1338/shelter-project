package org.unibl.etf.shelterapi.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unibl.etf.shelterapi.dto.ChangePasswordRequest;
import org.unibl.etf.shelterapi.dto.LoginRequest;
import org.unibl.etf.shelterapi.dto.RegisterRequest;
import org.unibl.etf.shelterapi.dto.UserResponse;
import org.unibl.etf.shelterapi.exception.EmailAlreadyUsedException;
import org.unibl.etf.shelterapi.exception.InvalidCredentialsException;
import org.unibl.etf.shelterapi.exception.InvalidPasswordException;
import org.unibl.etf.shelterapi.exception.ResourceNotFoundException;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;
import org.unibl.etf.shelterapi.repository.UserRepository;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottleService loginThrottleService;
    private final RefreshTokenService refreshTokenService;

    /** Compared against when the email does not exist, so the response time does not reveal it. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       LoginThrottleService loginThrottleService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginThrottleService = loginThrottleService;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim(),
                Role.USER);
        return toResponse(userRepository.save(user));
    }

    /**
     * Checks the credentials. The same InvalidCredentialsException is thrown for a wrong password and for
     * an unknown email, and failures are counted per email.
     */
    public User authenticate(LoginRequest request) {
        String email = normalize(request.email());
        loginThrottleService.checkAllowed(email);

        User user = userRepository.findByEmail(email).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !passwordMatches) {
            loginThrottleService.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        loginThrottleService.reset(email);
        return user;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(AuthService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    }

    /** Changes the password and signs the user out everywhere by revoking all refresh tokens. */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException();
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole());
    }
}