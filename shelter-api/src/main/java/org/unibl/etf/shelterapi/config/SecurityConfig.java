package org.unibl.etf.shelterapi.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        AdminProperties.class,
        RefreshTokenProperties.class,
        LoginThrottleProperties.class,
        CsrfProperties.class
})
public class SecurityConfig {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String USER_ROLE = "USER";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RefreshTokenProperties refreshProperties) {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF protection (double-submit cookie) is enforced only where a browser attaches
                // credentials by itself: /refresh and /logout use the refresh cookie. Every other endpoint
                // authenticates with the Authorization header, which a browser never adds on its own.
                // The token is issued as a cookie readable by the client (XSRF-TOKEN, SameSite=Strict) and
                // must be echoed in the X-XSRF-TOKEN header. The Origin check in OriginValidator stays as
                // an additional layer.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository(refreshProperties))
                        // The plain handler compares the raw cookie value; the default one expects a masked token.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // By default Spring drops the CSRF token whenever a request authenticates. There is no
                        // session to protect here, and the client reads the token only once per page load, so
                        // a dropped token would make the next logout fail.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        .requireCsrfProtectionMatcher(cookieAuthenticatedEndpoints()))
                // Without this filter the cookie would be written only when the token is read, so the client
                // would not have it before its first call to /refresh.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/info").permitAll()
                        // Spring forwards errors (for example validation failures) to /error
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // Care details are not public. These two rules must stay above the public GET rule.
                        // Who may read them is decided by the service: administrators and the approved sponsor.
                        .requestMatchers(HttpMethod.GET, "/api/dogs/*/care").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/dogs/*/care").hasRole(ADMIN_ROLE)
                        // Photos are shown through the public dog endpoints, but only admins change them.
                        // Without these two rules the paths would fall to anyRequest().authenticated().
                        .requestMatchers(HttpMethod.POST, "/api/dogs/*/images").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/dogs/*/images/*").hasRole(ADMIN_ROLE)
                        // Browsing dogs is public; creating, changing and deleting is for admins only.
                        .requestMatchers(HttpMethod.GET, "/api/dogs", "/api/dogs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/dogs").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.PUT, "/api/dogs/*").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/dogs/*").hasRole(ADMIN_ROLE)
                        // Sponsorships: users apply and cancel their own, admins review everything.
                        .requestMatchers(HttpMethod.POST, "/api/sponsorships", "/api/sponsorships/*/cancel")
                        .hasRole(USER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/sponsorships/me").authenticated()
                        .requestMatchers("/api/sponsorships", "/api/sponsorships/**").hasRole(ADMIN_ROLE)
                        // Visits: users book a visit to a dog, see their own visits and cancel them.
                        .requestMatchers(HttpMethod.POST, "/api/visits", "/api/visits/*/cancel").hasRole(USER_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/visits/me").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository(RefreshTokenProperties refreshProperties) {
        // HttpOnly is off on purpose: the client script has to read the value and send it back in a header.
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(refreshProperties.cookieSecure())
                .sameSite("Strict"));
        return repository;
    }

    private static RequestMatcher cookieAuthenticatedEndpoints() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/auth/refresh"),
                paths.matcher(HttpMethod.POST, "/api/auth/logout"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(signingKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Maps the "role" claim (for example ADMIN) to the Spring authority ROLE_ADMIN. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static SecretKey signingKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}