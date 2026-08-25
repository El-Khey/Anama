package com.novelrealm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;

import com.novelrealm.model.User;
import com.novelrealm.service.auth.AuthenticationService;
import com.novelrealm.service.user.UserService;

/**
 * Appelé par Spring Security UNE FOIS que le flux OAuth Google a réussi
 * (code échangé, infos récupérées). On greffe ici NOTRE logique métier :
 * réconcilier le compte (loginWithGoogle), créer NOTRE session, puis
 * rediriger vers le front.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final AuthenticationService authenticationService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuth2SuccessHandler(UserService userService,
                                AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // Récupérer les infos que Google a renvoyées.
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture"); // avatar par défaut du compte

        // Notre logique : retrouver ou créer le compte (réconciliation par email).
        User user = userService.loginWithGoogle(email, name, picture);

        // Émettre NOTRE JWT en cookie httpOnly (même mécanisme que le login classique).
        authenticationService.authenticate(user, response);

        // Renvoyer l'utilisateur vers le front, connecté.
        response.sendRedirect(frontendUrl + "/profil");
    }
}