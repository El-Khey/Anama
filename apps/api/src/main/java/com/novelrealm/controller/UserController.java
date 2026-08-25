package com.novelrealm.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.JsonNode;

import com.novelrealm.dto.user.ChangePasswordRequest;
import com.novelrealm.dto.user.UpdateProfileRequest;
import com.novelrealm.dto.user.UserResponse;
import com.novelrealm.dto.user.UserSearchResponse;
import com.novelrealm.dto.user.UserStatsResponse;
import com.novelrealm.exception.user.InvalidProfileFieldException;
import com.novelrealm.model.User;
import com.novelrealm.service.auth.AuthenticationService;
import com.novelrealm.service.common.FileStorageService;
import com.novelrealm.service.user.UserMapper;
import com.novelrealm.service.user.UserService;
import com.novelrealm.service.user.UserStatsService;

/**
 * API des utilisateurs. Les routes {@code /me/**} concernent toujours
 * l'utilisateur connecté ({@code authentication.getName()} = l'email posé au
 * login) — jamais d'id utilisateur dans l'URL.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;
    private final UserStatsService userStatsService;
    private final FileStorageService fileStorageService;
    private final AuthenticationService authenticationService;

    public UserController(
            UserService userService,
            UserMapper userMapper,
            UserStatsService userStatsService,
            FileStorageService fileStorageService,
            AuthenticationService authenticationService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.userStatsService = userStatsService;
        this.fileStorageService = fileStorageService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {
        List<UserResponse> body = userService.findAll().stream()
                .map(userMapper::toPublicResponse)
                .toList();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toPublicResponse(userService.findById(id)));
    }

    /**
     * GET /api/users/search?q=… — autocomplétion des mentions (issue #45, §2).
     * Huit résultats au plus, jamais soi-même, jamais d'email. Route littérale :
     * elle gagne sur {@code /{id}} lors du routage, aucun conflit.
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(
            @RequestParam("q") String query,
            Authentication authentication) {
        List<UserSearchResponse> body = userService.search(authentication.getName(), query).stream()
                .map(user -> new UserSearchResponse(
                        user.getId(), user.getPseudo(), user.getAvatarUrl()))
                .toList();
        return ResponseEntity.ok(body);
    }

    /** GET /api/users/me — profil complet de l'utilisateur connecté (préférences incluses). */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(userMapper.toOwnResponse(user));
    }

    /** PATCH /api/users/me — mise à jour partielle (pseudo, bio, préférences JSON). */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        User user = userService.updateProfile(
                authentication.getName(),
                request.pseudo(),
                request.bio(),
                serializePreferences(request.preferences()));
        return ResponseEntity.ok(userMapper.toOwnResponse(user));
    }

    /** PUT /api/users/me/password — change le mot de passe (comptes locaux uniquement). */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(
                authentication.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/users/me/stats — statistiques de lecture résumées (profil). */
    @GetMapping("/me/stats")
    public ResponseEntity<UserStatsResponse> myStats(Authentication authentication) {
        return ResponseEntity.ok(userStatsService.getStats(authentication.getName()));
    }

    /**
     * DELETE /api/users/me — supprime définitivement le compte (RGPD).
     * Efface les données (cascade SQL), les fichiers importés, puis ferme la
     * session : le client se retrouve déconnecté sans appel supplémentaire.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        List<String> uploads = userService.deleteAccount(authentication.getName());
        uploads.forEach(fileStorageService::deleteIfLocal);
        authenticationService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    // ── Avatar & bannière ───────────────────────────────────────────

    /** POST /api/users/me/avatar — importe l'avatar (multipart, champ « file »). */
    @PostMapping("/me/avatar")
    public ResponseEntity<UserResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String email = authentication.getName();
        User user = userService.findByEmail(email);

        String newUrl = fileStorageService.storeAvatar(file, user.getId());
        String previous = userService.updateAvatarUrl(email, newUrl);
        fileStorageService.deleteIfLocal(previous); // remplace → nettoie l'ancien fichier

        return ResponseEntity.ok(userMapper.toOwnResponse(userService.findByEmail(email)));
    }

    /** DELETE /api/users/me/avatar — retire l'avatar (retour aux initiales). */
    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserResponse> deleteAvatar(Authentication authentication) {
        String email = authentication.getName();
        String previous = userService.updateAvatarUrl(email, null);
        fileStorageService.deleteIfLocal(previous);
        return ResponseEntity.ok(userMapper.toOwnResponse(userService.findByEmail(email)));
    }

    /** POST /api/users/me/banner — importe la bannière de profil (multipart, champ « file »). */
    @PostMapping("/me/banner")
    public ResponseEntity<UserResponse> uploadBanner(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        String email = authentication.getName();
        User user = userService.findByEmail(email);

        String newUrl = fileStorageService.storeBanner(file, user.getId());
        String previous = userService.updateBannerUrl(email, newUrl);
        fileStorageService.deleteIfLocal(previous);

        return ResponseEntity.ok(userMapper.toOwnResponse(userService.findByEmail(email)));
    }

    /** DELETE /api/users/me/banner — retire la bannière. */
    @DeleteMapping("/me/banner")
    public ResponseEntity<UserResponse> deleteBanner(Authentication authentication) {
        String email = authentication.getName();
        String previous = userService.updateBannerUrl(email, null);
        fileStorageService.deleteIfLocal(previous);
        return ResponseEntity.ok(userMapper.toOwnResponse(userService.findByEmail(email)));
    }

    /**
     * Arbre JSON du PATCH → chaîne stockée. Conventions : champ absent = ne pas
     * toucher (null), {@code null} explicite = effacer (chaîne vide, interprétée
     * par le service), sinon un OBJET JSON obligatoirement.
     */
    private static String serializePreferences(JsonNode preferences) {
        if (preferences == null) {
            return null;
        }
        if (preferences.isNull()) {
            return "";
        }
        if (!preferences.isObject()) {
            throw new InvalidProfileFieldException("Préférences invalides (objet JSON attendu)");
        }
        return preferences.toString();
    }
}
