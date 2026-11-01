package org.unibl.etf.shelterapi.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.unibl.etf.shelterapi.config.JwtProperties;
import org.unibl.etf.shelterapi.model.Role;
import org.unibl.etf.shelterapi.model.User;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SIGNING_KEY = "unit-test-signing-key-with-more-than-32-bytes";

    @Test
    void generatedTokenCarriesSubjectRoleAndExpiry() {
        SecretKey key = new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtService service = new JwtService(encoder, new JwtProperties(SIGNING_KEY, Duration.ofHours(1)));
        User user = new User("ana@example.com", "hashed", "Ana", "Anic", Role.ADMIN);

        Jwt jwt = decoder.decode(service.generateToken(user));

        assertEquals("ana@example.com", jwt.getSubject());
        assertEquals("ADMIN", jwt.getClaimAsString("role"));
        assertEquals(Duration.ofHours(1), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertEquals(3600L, service.getExpiresInSeconds());
    }

    @Test
    void signingKeyShorterThan32BytesIsRejected() {
        Duration expiration = Duration.ofHours(1);

        assertThrows(IllegalArgumentException.class, () -> new JwtProperties("too-short", expiration));
    }

    @Test
    void missingSigningKeyIsRejected() {
        Duration expiration = Duration.ofHours(1);

        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(null, expiration));
    }
}
