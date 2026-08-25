package com.novelrealm.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.Duration;

import com.novelrealm.model.User;

/**
 * Plomberie d'authentification, désormais <b>stateless</b> : à partir d'un
 * utilisateur déjà vérifié, émet un JWT (via {@link JwtService}), le pose en
 * cookie httpOnly (transport web) et le renvoie (transport mobile).
 * Un seul token, deux transports.
 */
@Service
public class AuthenticationService {

    /**
     * En-tête de réponse portant un token ré-émis par le renouvellement glissant
     * (issue #45, §1). Le client mobile remplace silencieusement son token quand il
     * le voit passer ; le web, lui, reçoit le même token via le cookie httpOnly et
     * n'a rien à faire. <b>Doit rester identique à la constante côté mobile</b>
     * ({@code AuthInterceptor.REFRESHED_TOKEN_HEADER}).
     */
    public static final String REFRESHED_TOKEN_HEADER = "X-Refreshed-Token";

    private final JwtService jwtService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final long expirationSeconds;

    public AuthenticationService(
            JwtService jwtService,
            @Value("${app.jwt.cookie-name}") String cookieName,
            @Value("${app.jwt.cookie-secure}") boolean cookieSecure,
            @Value("${app.jwt.cookie-same-site}") String cookieSameSite,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Émet le JWT de l'utilisateur, le pose en cookie httpOnly sur la réponse
     * (web) et le retourne (mobile).
     */
    public String authenticate(User user, HttpServletResponse response) {
        String token = jwtService.generate(user.getEmail());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, expirationSeconds).toString());
        return token;
    }

    /**
     * Ré-émet un token frais pour un utilisateur déjà authentifié (renouvellement
     * glissant, issue #45, §1) : nouveau cookie httpOnly pour le web, en-tête
     * {@link #REFRESHED_TOKEN_HEADER} pour le mobile. Les deux transports sont
     * servis à chaque fois — le serveur ne sait pas d'où vient la requête, et un
     * cookie surnuméraire côté mobile ne gêne personne.
     *
     * <p>Appelé par le filtre JWT quand le token courant a dépassé la moitié de sa
     * vie : un utilisateur actif ne voit donc jamais son token expirer, seule une
     * vraie période d'inactivité (la durée complète du token) le déconnecte.
     */
    public void refresh(String email, HttpServletResponse response) {
        String token = jwtService.generate(email);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, expirationSeconds).toString());
        response.setHeader(REFRESHED_TOKEN_HEADER, token);
    }

    /**
     * Déconnexion : efface le cookie JWT (web) et vide le contexte. Le client
     * mobile jette simplement son token. Une éventuelle session résiduelle du
     * flux OAuth2 Google (web) est invalidée par précaution.
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
        SecurityContextHolder.clearContext();
    }

    /** Construit le cookie JWT (maxAge=0 pour l'effacer à la déconnexion). */
    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite(cookieSameSite)
                .build();
    }
}
