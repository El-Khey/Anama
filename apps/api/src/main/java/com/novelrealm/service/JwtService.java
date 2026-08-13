package com.novelrealm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Génère et valide les JWT applicatifs. Le <b>sujet</b> du token est l'email de
 * l'utilisateur : c'est ce que les contrôleurs lisent déjà via
 * {@code authentication.getName()}, donc rien à changer côté métier.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expirationSeconds;

    public JwtService(JwtEncoder encoder,
                      JwtDecoder decoder,
                      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.expirationSeconds = expirationSeconds;
    }

    /** Émet un JWT signé (HS256) dont le sujet est l'email fourni. */
    public String generate(String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(email)
                .issuedAt(now)
                .expiresAt(now.plus(expirationSeconds, ChronoUnit.SECONDS))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Valide le token (signature + expiration) et renvoie l'email (subject).
     * Lève une {@link org.springframework.security.oauth2.jwt.JwtException} si
     * le token est invalide ou expiré.
     */
    public String extractEmail(String token) {
        return decoder.decode(token).getSubject();
    }

    /**
     * Valide le token et le renvoie <b>entier</b> — sujet ET horodatages. C'est ce
     * dont le filtre a besoin pour le renouvellement glissant : savoir si le token
     * a dépassé la moitié de sa vie demande {@code iat} et {@code exp}, pas
     * seulement l'email. Mêmes exceptions que {@link #extractEmail}.
     */
    public Jwt decode(String token) {
        return decoder.decode(token);
    }
}
