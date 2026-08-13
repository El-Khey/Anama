package com.novelrealm.service;

import com.novelrealm.exception.EmailAlreadyUsedException;
import com.novelrealm.exception.InvalidProfileFieldException;
import com.novelrealm.exception.PasswordChangeNotAllowedException;
import com.novelrealm.exception.UserNotFoundException;
import com.novelrealm.model.User;
import com.novelrealm.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.novelrealm.exception.InvalidCredentialsException;
import com.novelrealm.model.User.AuthProvider;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public User register(String pseudo, String email, String rawPassword, AuthProvider provider) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        String hashedPassword = passwordEncoder.encode(rawPassword); // ← le hashage

        User user = new User(pseudo, email, hashedPassword,provider); // ← on stocke le hash
        User savedUser = userRepository.save(user);
        
        try {
            emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getPseudo());
        } catch (Exception e) {
            // Log the error but don't fail the registration
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }
        return savedUser;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public User login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());

        // Un compte Google n'a pas de mot de passe → login par mot de passe impossible.
        if (user.getProvider() == User.AuthProvider.GOOGLE) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }

    public User loginWithGoogle(String email, String pseudo, String pictureUrl) {
        // Stratégie A : on cherche un compte existant par email.
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // Pas de compte → on en crée un "GOOGLE" (sans mot de passe).
                    User user = new User(pseudo, email, null, User.AuthProvider.GOOGLE);
                    // La photo Google sert d'avatar par défaut (modifiable ensuite).
                    user.setAvatarUrl(pictureUrl);
                    User savedUser = userRepository.save(user);
                    try {
                        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getPseudo());
                    } catch (Exception e) {
                        System.err.println("Failed to send welcome email: " + e.getMessage());
                    }
                    return savedUser;
                });
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    /**
     * Autocomplétion des mentions (issue #45, §2) : les pseudos contenant la
     * saisie, huit au plus, sans jamais l'utilisateur lui-même — se mentionner
     * soi-même ne sert à rien, autant ne pas l'offrir.
     */
    @Transactional(readOnly = true)
    public List<User> search(String requesterEmail, String query) {
        String cleaned = query == null ? "" : query.strip();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        User self = findByEmail(requesterEmail);
        return userRepository.findTop8ByPseudoContainingIgnoreCaseOrderByPseudoAsc(cleaned).stream()
                .filter(user -> !user.getId().equals(self.getId()))
                .toList();
    }

    // ── Profil (issue #17) ──────────────────────────────────────────

    /** Taille maximale du JSON de préférences (garde-fou anti-abus). */
    private static final int MAX_PREFERENCES_LENGTH = 20_000;

    /**
     * Mise à jour PARTIELLE du profil : chaque paramètre {@code null} est
     * laissé tel quel. Une bio vide efface la bio.
     */
    @Transactional
    public User updateProfile(String email, String pseudo, String bio, String preferencesJson) {
        User user = findByEmail(email);

        if (pseudo != null) {
            String cleaned = pseudo.strip();
            if (cleaned.length() < 3 || cleaned.length() > 30) {
                throw new InvalidProfileFieldException("Le pseudo doit faire entre 3 et 30 caractères");
            }
            user.setPseudo(cleaned);
        }
        if (bio != null) {
            String cleaned = bio.strip();
            user.setBio(cleaned.isEmpty() ? null : cleaned);
        }
        if (preferencesJson != null) {
            if (preferencesJson.length() > MAX_PREFERENCES_LENGTH) {
                throw new InvalidProfileFieldException("Préférences trop volumineuses");
            }
            // Chaîne vide = effacement explicite (cf. UserController).
            user.setPreferences(preferencesJson.isEmpty() ? null : preferencesJson);
        }
        return user; // @Transactional → flush automatique (dirty checking)
    }

    /**
     * Change le mot de passe après vérification de l'actuel. Refusé pour les
     * comptes Google (pas de mot de passe local).
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = findByEmail(email);

        if (user.getProvider() == AuthProvider.GOOGLE || user.getPassword() == null) {
            throw new PasswordChangeNotAllowedException();
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    /** Remplace l'URL d'avatar (null = suppression) et renvoie l'ancienne pour nettoyage disque. */
    @Transactional
    public String updateAvatarUrl(String email, String avatarUrl) {
        User user = findByEmail(email);
        String previous = user.getAvatarUrl();
        user.setAvatarUrl(avatarUrl);
        return previous;
    }

    /** Remplace l'URL de bannière (null = suppression) et renvoie l'ancienne pour nettoyage disque. */
    @Transactional
    public String updateBannerUrl(String email, String bannerUrl) {
        User user = findByEmail(email);
        String previous = user.getBannerUrl();
        user.setBannerUrl(bannerUrl);
        return previous;
    }

    /**
     * Supprime DÉFINITIVEMENT le compte. Tout ce qui en dépend (bibliothèque,
     * étagères, progression, favoris, avis) part avec lui : les clés étrangères
     * sont déclarées {@code ON DELETE CASCADE} côté base.
     *
     * @return les URLs des fichiers importés (avatar, bannière) que l'appelant
     *         doit effacer du disque — la base ne les nettoie pas.
     */
    @Transactional
    public List<String> deleteAccount(String email) {
        User user = findByEmail(email);
        List<String> uploads = Stream.of(user.getAvatarUrl(), user.getBannerUrl())
                .filter(Objects::nonNull)
                .toList();
        userRepository.delete(user);
        return uploads;
    }
}