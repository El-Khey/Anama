package com.novelrealm.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un message accroché à un bloc du chapitre (#41, §4).
 *
 * <p>Seul l'index du bloc est transmis : l'empreinte du texte est calculée par le
 * serveur à partir du chapitre, comme pour les citations. Un client ne peut donc pas
 * accrocher un message à un passage qui n'existe pas.
 *
 * <p>Pas de bornes intra-bloc ici, contrairement aux citations : on commente le
 * paragraphe, pas une phrase choisie dedans. Citer vise une phrase parce qu'on la
 * recopie ; réagir vise le moment, et le moment tient dans le paragraphe.
 *
 * <p><b>{@code body} n'est plus {@code @NotBlank}</b> depuis l'issue #45, §5 : un
 * message peut être un GIF seul. « Du texte OU un GIF » est une règle
 * inter-champs, tenue par le service.
 */
public record CreatePassageCommentRequest(
        @NotNull(message = "Le bloc est obligatoire")
        @Min(value = 0, message = "Index de bloc invalide")
        Integer blockIndex,

        @Size(max = 1_000, message = "Le message ne peut pas dépasser 1000 caractères")
        String body,

        /**
         * Masque le message tant que le lecteur ne le révèle pas (#41, §7).
         *
         * <p><b>Objet et non {@code boolean} primitif, volontairement.</b> Jackson
         * n'applique pas de valeur par défaut aux composants d'un record : une
         * propriété absente du JSON y arrive à {@code null}, ce qui échoue aussitôt
         * sur un primitif — {@code Cannot map null into type boolean}. Or un client
         * a toutes les raisons d'omettre un champ facultatif laissé à sa valeur par
         * défaut, et c'est exactement ce que fait {@code kotlinx.serialization}.
         * Absent vaut donc « pas un spoiler », comme un lecteur s'y attend.
         */
        Boolean spoiler,

        /**
         * Message auquel on répond, ou {@code null} pour ouvrir un fil. Répondre à une
         * réponse est accepté : le serveur re-rattache au fil racine plutôt que de
         * refuser, parce que du point de vue du lecteur le geste est le même.
         */
        Long parentId,

        /** Mentions résolues par l'autocomplétion (issue #45, §2) — re-vérifiées par le serveur. */
        List<Long> mentionedUserIds,

        /** URL du fournisseur pour la version animée (issue #45, §5). */
        @Size(max = 500, message = "URL de GIF trop longue")
        String gifUrl,

        /** URL du fournisseur pour l'image figée affichée par défaut. */
        @Size(max = 500, message = "URL de GIF trop longue")
        String gifPreviewUrl
) {
    /** {@code true} seulement si le client l'a explicitement demandé. */
    public boolean isSpoiler() {
        return Boolean.TRUE.equals(spoiler);
    }
}
