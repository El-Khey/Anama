package com.novelrealm.service.common;

import java.util.Optional;

/**
 * Valide qu'une chaîne est bien un emoji, et rien d'autre — partagé par les réactions
 * de commentaire et les réactions de bloc, qui ouvrent toutes deux le clavier complet.
 *
 * <p><b>Pourquoi valider plutôt que faire confiance ?</b> Dès qu'on quitte le jeu fermé
 * de six emojis, on ne peut plus comparer à une liste. Un client modifié pourrait alors
 * envoyer du texte arbitraire qui s'afficherait comme une fausse réaction pour tout le
 * monde. On borne donc la longueur et on exige que la chaîne soit faite d'emojis.
 *
 * <p>Sans exception propre : {@link #sanitize(String)} renvoie l'emoji nettoyé ou vide,
 * et c'est à l'appelant de lever l'erreur de son domaine ({@code InvalidComment...} ou
 * {@code InvalidPassage...}) — la validation ne connaît pas ces types.
 */
public final class EmojiValidation {

    /**
     * Longueur maximale d'un emoji, en points de code. Un emoji composé (drapeau,
     * famille, teinte de peau) tient largement sous cette borne ; au-delà, ce n'est
     * plus un emoji mais du texte, et on refuse.
     */
    private static final int MAX_EMOJI_CODEPOINTS = 8;

    private EmojiValidation() {
    }

    /**
     * L'emoji nettoyé (espaces retirés), ou {@link Optional#empty()} si la chaîne n'est
     * pas un emoji valide — trop longue, vide, ou contenant lettres/chiffres/ponctuation.
     */
    public static Optional<String> sanitize(String raw) {
        String emoji = raw == null ? "" : raw.strip();
        int[] codePoints = emoji.codePoints().toArray();
        if (codePoints.length == 0 || codePoints.length > MAX_EMOJI_CODEPOINTS) {
            return Optional.empty();
        }
        boolean anyEmoji = false;
        for (int cp : codePoints) {
            if (isEmojiScalar(cp)) {
                anyEmoji = true;
            } else if (!isEmojiModifierOrJoiner(cp)) {
                // Une lettre, un chiffre, une ponctuation ordinaire : refusé.
                return Optional.empty();
            }
        }
        return anyEmoji ? Optional.of(emoji) : Optional.empty();
    }

    /** Un point de code qui, à lui seul, dessine un emoji (pictogrammes, symboles). */
    private static boolean isEmojiScalar(int cp) {
        int type = Character.getType(cp);
        if (type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL) {
            return true;
        }
        // Emoji « clavier » construits sur des caractères ASCII (chiffres, #, *)
        // suivis du sélecteur de variante — le chiffre seul ne passe QUE s'il est
        // accompagné d'un point de code emoji réel dans la même séquence (anyEmoji),
        // un « 3 » nu échouant à la fin.
        return (cp >= 0x1F000 && cp <= 0x1FAFF)   // blocs Supplemental Symbols/Pictographs
                || (cp >= 0x2600 && cp <= 0x27BF)  // Misc Symbols + Dingbats
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF); // indicateurs régionaux (drapeaux)
    }

    /** Modificateurs et liants d'une séquence emoji : teinte, jointure ZWJ, variante. */
    private static boolean isEmojiModifierOrJoiner(int cp) {
        return cp == 0x200D                       // Zero Width Joiner
                || cp == 0xFE0F || cp == 0xFE0E    // sélecteurs de variante
                || (cp >= 0x1F3FB && cp <= 0x1F3FF) // teintes de peau
                || (cp >= 0x20E0 && cp <= 0x20FF)  // combinants (keycap enclosant)
                || (cp >= 0xE0020 && cp <= 0xE007F); // tags (drapeaux régionaux)
    }
}
