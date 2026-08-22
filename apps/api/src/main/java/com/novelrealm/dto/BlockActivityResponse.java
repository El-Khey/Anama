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
 * <p>{@code myReactions} liste les emojis que le lecteur a lui-même posés sur ce bloc
 * (vide s'il n'a pas réagi) — depuis le passage au multi-emoji, il peut y en avoir
 * plusieurs. Les donner ici évite un second appel : les puces doivent savoir
 * lesquelles surligner dès l'ouverture du chapitre.
 */
public record BlockActivityResponse(
        int blockIndex,
        long commentCount,
        List<EmojiTallyResponse> reactions,
        List<String> myReactions
) {}
