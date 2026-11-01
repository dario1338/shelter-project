package org.unibl.etf.shelterapi.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.Locale;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthResponse(
                jwtService.generateToken(user),
                TOKEN_TYPE,
                jwtService.getExpiresInSeconds(),
                user.getRole().name());
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(AuthService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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
