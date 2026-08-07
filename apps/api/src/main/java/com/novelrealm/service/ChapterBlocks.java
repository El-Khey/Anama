package com.novelrealm.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Découpage d'un chapitre en <b>blocs</b>, et empreinte d'un bloc — le socle de
 * l'ancre de passage (#41, §2).
 *
 * <p><b>Règle de découpage.</b> Un bloc = une ligne non vide du contenu, débarrassée
 * de ses espaces de bord. Cette règle est <b>dupliquée côté application mobile</b>
 * (voir {@code ReaderScreen.ChapterBody}) : les deux DOIVENT rester identiques,
 * sinon un index de bloc ne désigne pas le même texte de part et d'autre. Toute
 * évolution du découpage se fait ici ET là-bas, dans le même commit.
 *
 * <p><b>Pourquoi « bloc » et pas « paragraphe ».</b> Aujourd'hui un bloc est
 * toujours du texte, mais le mot est choisi pour que le jour où un chapitre
 * contiendrait autre chose (une illustration), ce soit un bloc de plus et rien à
 * refaire.
 *
 * <p><b>L'empreinte</b> est calculée sur le texte <i>normalisé</i> (suites d'espaces
 * ramenées à une seule) : un simple reformatage de la source ne doit pas tuer les
 * ancres. Elle est tronquée à 16 caractères hexadécimaux — 64 bits, largement assez
 * pour distinguer les quelques centaines de blocs d'un chapitre, et bien plus court
 * à stocker.
 */
public final class ChapterBlocks {

    /** Longueur de l'empreinte stockée, en caractères hexadécimaux. */
    public static final int HASH_LENGTH = 16;

    /**
     * Rayon de recherche autour de l'index attendu avant de balayer tout le
     * chapitre. Un chapitre ré-ingéré gagne ou perd typiquement quelques blocs :
     * on les retrouve tout de suite, sans parcourir le reste.
     */
    private static final int NEIGHBOUR_RADIUS = 5;

    private ChapterBlocks() {
    }

    /** Le chapitre découpé en blocs, dans l'ordre. Voir la règle en tête de classe. */
    public static List<String> split(String content) {
        List<String> blocks = new ArrayList<>();
        if (content == null) {
            return blocks;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }
        return blocks;
    }

    /** Empreinte d'un bloc, insensible aux variations d'espacement. */
    public static String hash(String block) {
        String normalized = block == null ? "" : block.strip().replaceAll("\\s+", " ");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti par la plateforme Java : si elle manque, ce n'est
            // pas un cas métier à gérer mais une JVM cassée.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Retrouve le bloc désigné par une ancre dans la version ACTUELLE du chapitre.
     *
     * <p>D'abord à l'index mémorisé ; sinon dans les blocs voisins ; sinon dans tout
     * le chapitre. Si l'empreinte ne correspond nulle part, l'ancre est déclarée
     * <b>morte</b> : on préfère avouer qu'on a perdu le passage plutôt que de pointer
     * un texte qui n'est pas celui d'origine.
     *
     * @return l'index courant du bloc, ou {@link Resolution#dead()} s'il a disparu
     */
    public static Resolution resolve(List<String> blocks, int storedIndex, String storedHash) {
        if (storedHash == null || blocks.isEmpty()) {
            return Resolution.dead();
        }
        if (matches(blocks, storedIndex, storedHash)) {
            return new Resolution(true, storedIndex);
        }
        for (int radius = 1; radius <= NEIGHBOUR_RADIUS; radius++) {
            if (matches(blocks, storedIndex - radius, storedHash)) {
                return new Resolution(true, storedIndex - radius);
            }
            if (matches(blocks, storedIndex + radius, storedHash)) {
                return new Resolution(true, storedIndex + radius);
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            if (hash(blocks.get(i)).equals(storedHash)) {
                return new Resolution(true, i);
            }
        }
        return Resolution.dead();
    }

    private static boolean matches(List<String> blocks, int index, String storedHash) {
        return index >= 0 && index < blocks.size() && hash(blocks.get(index)).equals(storedHash);
    }

    /** Résultat de {@link #resolve} : le bloc a-t-il été retrouvé, et où. */
    public record Resolution(boolean alive, int blockIndex) {
        public static Resolution dead() {
            return new Resolution(false, -1);
        }
    }
}
