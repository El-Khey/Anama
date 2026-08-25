package com.novelrealm.service.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.novelrealm.exception.common.InvalidUploadException;

/**
 * Stockage disque des images de profil (avatars, bannières) — stratégie simple
 * de l'issue #17 : dossier local servi en statique sous {@code /uploads/**}
 * (cf. {@code UploadsConfig}), chemin public stocké en base.
 *
 * <p>Validation par « magic bytes » (JPEG, PNG, WebP) : on ne fait pas
 * confiance au nom de fichier ni au Content-Type annoncés par le client.
 */
@Service
public class FileStorageService {

    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024; // 2 Mo
    private static final long BANNER_MAX_BYTES = 4L * 1024 * 1024; // 4 Mo

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Enregistre un avatar (≤ 2 Mo) et renvoie son chemin public {@code /uploads/avatars/…}. */
    public String storeAvatar(MultipartFile file, Long userId) {
        return store(file, userId, "avatars", AVATAR_MAX_BYTES, "2 Mo");
    }

    /** Enregistre une bannière (≤ 4 Mo) et renvoie son chemin public {@code /uploads/banners/…}. */
    public String storeBanner(MultipartFile file, Long userId) {
        return store(file, userId, "banners", BANNER_MAX_BYTES, "4 Mo");
    }

    /**
     * Supprime un fichier précédemment stocké, à partir de son chemin public.
     * Sans effet pour une URL externe (photo Google) ou un chemin hors du
     * dossier d'upload (protection path-traversal). Best-effort : une erreur
     * disque n'interrompt jamais l'opération métier appelante.
     */
    public void deleteIfLocal(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith("/uploads/")) {
            return;
        }
        Path target = root.resolve(publicUrl.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // Nettoyage best-effort : un orphelin sur disque vaut mieux qu'une 500.
        }
    }

    private String store(MultipartFile file, Long userId, String subdir, long maxBytes, String maxLabel) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Aucun fichier reçu");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidUploadException("Fichier trop volumineux (max " + maxLabel + ")");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier envoyé impossible", e);
        }

        String extension = detectImageExtension(bytes);
        if (extension == null) {
            throw new InvalidUploadException("Format non pris en charge (JPG, PNG ou WebP)");
        }

        String filename = "u" + userId + "-" + UUID.randomUUID() + "." + extension;
        Path dir = root.resolve(subdir);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du fichier impossible", e);
        }

        return "/uploads/" + subdir + "/" + filename;
    }

    /** Signature binaire → extension ({@code jpg}/{@code png}/{@code webp}), ou null si inconnue. */
    private static String detectImageExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
