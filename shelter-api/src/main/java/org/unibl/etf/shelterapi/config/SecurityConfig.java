package org.unibl.etf.shelterapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                // Stateless API: no session cookie is issued, so there is nothing for CSRF to protect.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/info").permitAll()
                        // Browsing dogs is public; creating, changing and deleting requires authentication.
                        .requestMatchers(HttpMethod.GET, "/api/dogs", "/api/dogs/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}