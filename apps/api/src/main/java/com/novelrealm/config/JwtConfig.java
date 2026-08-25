package com.novelrealm.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Beans de signature/vérification JWT basés sur <b>Nimbus</b> (déjà présent via
 * {@code spring-boot-starter-oauth2-client}) — on évite ainsi jjwt/java-jwt qui
 * tirent Jackson 2 alors que Spring Boot 4 est en Jackson 3.
 *
 * <p>Signature symétrique <b>HMAC (HS256)</b> avec un secret partagé (env).
 */
@Configuration
public class JwtConfig {

    private final SecretKey key;

    public JwtConfig(@Value("${app.jwt.secret}") String secret) {
        // HS256 exige une clé d'au moins 256 bits (32 octets).
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Valide la signature ET l'expiration (exp) à chaque décodage.
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
