package com.novelrealm.dto;

import java.util.List;

/**
 * L'activité d'un bloc, telle que la marge du lecteur doit la montrer (#41, §4).
 *
 * <p>{@code blockIndex} est l'index <b>résolu</b> : celui où le passage se trouve
 * aujourd'hui, pas celui enregistré à l'écriture. Un chapitre ré-ingéré décale ses
 * blocs ; c'est le serveur qui recale, pour que l'app n'ait jamais à connaître les
 * empreintes.
 *
 * <p>{@code myEmoji} vaut {@code null} tant que le lecteur n'a pas réagi. Le donner
 * ici évite un second appel : la barre de réaction doit savoir quel emoji surligner
 * dès l'ouverture.
 */
public record BlockActivityResponse(
        int blockIndex,
        long commentCount,
        List<EmojiTallyResponse> reactions,
        String myEmoji
) {}
