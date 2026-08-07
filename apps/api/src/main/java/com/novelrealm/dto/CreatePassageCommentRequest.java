package com.novelrealm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 */
public record CreatePassageCommentRequest(
        @NotNull(message = "Le bloc est obligatoire")
        @Min(value = 0, message = "Index de bloc invalide")
        Integer blockIndex,

        @NotBlank(message = "Le message ne peut pas être vide")
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
        Long parentId
) {
    /** {@code true} seulement si le client l'a explicitement demandé. */
    public boolean isSpoiler() {
        return Boolean.TRUE.equals(spoiler);
    }
}
