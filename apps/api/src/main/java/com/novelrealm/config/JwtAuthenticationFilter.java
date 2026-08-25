package com.novelrealm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.novelrealm.service.auth.AuthenticationService;
import com.novelrealm.service.auth.JwtService;

/**
 * Authentifie chaque requête à partir d'un JWT, cherché d'abord dans le header
 * {@code Authorization: Bearer …} (client mobile), sinon dans un cookie httpOnly
 * (client web). Un token valide → un {@code Authentication} dont le nom est
 * l'email, exactement comme l'ancienne session ; les contrôleurs ne changent pas.
 *
 * <p><b>Renouvellement glissant (issue #45, §1).</b> Un token qui a dépassé la
 * moitié de sa vie est ré-émis dans la même réponse : cookie rafraîchi pour le
 * web, en-tête {@code X-Refreshed-Token} pour le mobile. Un utilisateur actif ne
 * retombe donc jamais sur l'écran de connexion ; seul un silence d'une durée de
 * vie complète l'y ramène. La mi-vie — et non chaque requête — pour que le
 * travail de signature reste exceptionnel et que les réponses ordinaires ne
 * transportent pas de Set-Cookie inutile.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthenticationService authenticationService;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   AuthenticationService authenticationService,
                                   @Value("${app.jwt.cookie-name}") String cookieName) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Jwt jwt = jwtService.decode(token);
                String email = jwt.getSubject();
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(email, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Ici et pas après le doFilter : les en-têtes doivent partir AVANT
                // que le contrôleur ne commence à écrire le corps de la réponse.
                maybeSlideExpiration(jwt, email, response);
            } catch (JwtException ex) {
                // Token invalide ou expiré : requête laissée anonyme → 401 en aval.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Ré-émet le token s'il a dépassé la moitié de sa vie. Un token sans
     * horodatages (jamais produit par {@code JwtService}, mais rien ne l'interdit
     * formellement) est simplement laissé courir jusqu'à son expiration.
     */
    private void maybeSlideExpiration(Jwt jwt, String email, HttpServletResponse response) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        if (issuedAt == null || expiresAt == null) {
            return;
        }
        Instant halfLife = issuedAt.plus(Duration.between(issuedAt, expiresAt).dividedBy(2));
        if (Instant.now().isAfter(halfLife)) {
            authenticationService.refresh(email, response);
        }
    }

    /** Header `Authorization: Bearer …` (mobile) prioritaire, sinon cookie (web). */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
