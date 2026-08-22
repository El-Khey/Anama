package com.novelrealm.mobile.ui.components

/**
 * Catalogue d'emojis pour le sélecteur complet (bouton « + » des réactions).
 *
 * <p><b>Pourquoi une liste en dur ?</b> Android n'offre pas de sélecteur d'emoji
 * qu'on ouvre à la demande hors d'un champ de saisie, et embarquer une librairie
 * de picker imposerait une dépendance et un style qu'on ne maîtrise pas. Une liste
 * tenue à la main reste modeste, cohérente avec le reste de l'app, et se cherche par
 * mots-clés (voir [keywords]) plutôt que par un défilement interminable.
 *
 * <p>Le jeu n'est pas exhaustif — inutile : on vise les réactions à un message de
 * lecture, pas l'expression de tout l'Unicode. Chaque emoji porte quelques mots-clés
 * FR pour que « rire », « feu » ou « cœur » tombent juste.
 */
object EmojiCatalog {

    /** Une catégorie du sélecteur : un libellé d'onglet et ses emojis. */
    data class Category(val label: String, val emojis: List<String>)

    /**
     * Les six emojis de la barre rapide (appui long) — miroir de
     * `PassageRepository.EMOJIS`, dans le même ordre. Réutilisés tels quels pour la
     * réaction éclair, avant même d'ouvrir le sélecteur complet.
     */
    val QUICK = listOf("❤️", "😂", "🤯", "😭", "🔥", "😱")

    val CATEGORIES: List<Category> = listOf(
        Category(
            "Sourires",
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😋",
                "😛", "😜", "🤪", "😝", "🤗", "🤭", "🤫", "🤔", "🤨", "😐",
                "😑", "😶", "😏", "😒", "🙄", "😬", "😮‍💨", "😌", "😔", "😴",
                "😪", "🤤", "😷", "🤒", "🤕", "🤢", "🤮", "🥵", "🥶", "🥴",
                "😵", "🤯", "🥳", "🥺", "😎", "🤓", "🧐",
            ),
        ),
        Category(
            "Peines",
            listOf(
                "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥱", "😦",
                "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞",
                "😓", "😩", "😫", "😤", "😡", "😠", "🤬", "😈", "👿", "💀",
                "☠️", "💩", "🤡", "👻", "👽", "🤖",
            ),
        ),
        Category(
            "Gestes",
            listOf(
                "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
                "👈", "👉", "👆", "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖",
                "👏", "🙌", "👐", "🤲", "🙏", "🤝", "💪", "🦾", "✍️", "💅",
                "👀", "🫡", "🫢", "🫣", "🫰", "🫶",
            ),
        ),
        Category(
            "Cœurs",
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "❤️‍🔥",
                "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💬", "🗯️", "💤",
            ),
        ),
        Category(
            "Symboles",
            listOf(
                "🔥", "⭐", "🌟", "✨", "⚡", "☀️", "🌈", "☁️", "❄️", "🌙",
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🎯", "✅", "❌", "❓",
                "❗", "‼️", "⁉️", "💡", "🔔", "🚀", "👑", "💎", "🎵", "🎶",
            ),
        ),
        Category(
            "Nature",
            listOf(
                "🐶", "🐱", "🦊", "🐻", "🐼", "🐨", "🦁", "🐯", "🐸", "🐵",
                "🐔", "🐧", "🦅", "🦆", "🦉", "🐺", "🐗", "🐴", "🦄", "🐝",
                "🦋", "🐢", "🐍", "🐙", "🦑", "🐬", "🐳", "🐟", "🌵", "🌸",
                "🌹", "🌻", "🍀", "🍄", "🌍", "🌊",
            ),
        ),
        Category(
            "À table",
            listOf(
                "🍏", "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍑", "🍍",
                "🥑", "🍅", "🌶️", "🌽", "🍔", "🍟", "🍕", "🌮", "🌯", "🍜",
                "🍣", "🍰", "🎂", "🍪", "🍫", "🍩", "☕", "🍺", "🍷", "🥂",
            ),
        ),
    )

    /**
     * Recherche par mot-clé. On indexe quelques termes FR par emoji ; un terme absent
     * de l'index laisse simplement l'emoji hors du résultat, ce n'est pas une erreur.
     * La recherche est insensible aux accents et à la casse (voir [normalize]).
     */
    private val keywords: Map<String, List<String>> = mapOf(
        "😂" to listOf("rire", "mdr", "lol", "drole", "pleure de rire"),
        "🤣" to listOf("rire", "mdr", "roule", "explose"),
        "❤️" to listOf("coeur", "amour", "adore", "rouge"),
        "🥰" to listOf("amour", "coeur", "adorable"),
        "😍" to listOf("amour", "yeux", "coeur", "adore"),
        "🔥" to listOf("feu", "chaud", "flamme", "genial", "envoie"),
        "🤯" to listOf("choc", "explose", "retournement", "mind blown"),
        "😱" to listOf("peur", "cri", "choc", "glacant", "horreur"),
        "😭" to listOf("pleure", "triste", "larmes", "brise"),
        "😢" to listOf("pleure", "triste", "larme"),
        "👍" to listOf("pouce", "ok", "bien", "d'accord", "oui"),
        "👎" to listOf("pouce", "non", "mauvais", "pas d'accord"),
        "🙏" to listOf("merci", "priere", "svp", "supplie"),
        "👏" to listOf("bravo", "applaudir", "applaudissement"),
        "💯" to listOf("cent", "parfait", "total", "100"),
        "💀" to listOf("mort", "crane", "mdr", "tue"),
        "👀" to listOf("yeux", "regarde", "voir", "attention"),
        "🎉" to listOf("fete", "bravo", "celebration", "cotillon"),
        "🚀" to listOf("fusee", "rapide", "decolle", "espace"),
        "👑" to listOf("couronne", "roi", "reine", "boss"),
        "✅" to listOf("valide", "coche", "ok", "fait", "vrai"),
        "❌" to listOf("faux", "non", "croix", "erreur"),
        "🤔" to listOf("reflexion", "pense", "hmm", "doute"),
        "😎" to listOf("cool", "lunettes", "classe"),
        "🥳" to listOf("fete", "anniversaire", "celebration"),
        "🐶" to listOf("chien", "toutou", "animal"),
        "🐱" to listOf("chat", "minou", "animal"),
        "🦄" to listOf("licorne", "magie"),
        "🍕" to listOf("pizza", "manger", "nourriture"),
        "☕" to listOf("cafe", "boisson", "the"),
        "⭐" to listOf("etoile", "favori", "note"),
        "✨" to listOf("etincelle", "brille", "magie", "propre"),
        "⚡" to listOf("eclair", "foudre", "rapide", "electrique"),
        "🌈" to listOf("arc en ciel", "couleur"),
    )

    /** Tous les emojis, catégories aplaties — utile pour une recherche globale. */
    private val all: List<String> = CATEGORIES.flatMap { it.emojis }.distinct()

    /**
     * Les emojis correspondant à [rawQuery]. Une requête vide ne renvoie rien : le
     * sélecteur montre alors ses catégories plutôt qu'une liste plate. On cherche à la
     * fois dans les mots-clés indexés et dans le glyphe lui-même (coller un emoji dans
     * la barre le retrouve).
     */
    fun search(rawQuery: String): List<String> {
        val q = normalize(rawQuery)
        if (q.isBlank()) return emptyList()
        return all.filter { emoji ->
            emoji == rawQuery.trim() ||
                keywords[emoji]?.any { normalize(it).contains(q) } == true
        }
    }

    /** Minuscule + sans accents, pour que « éclair » et « eclair » tombent pareil. */
    private fun normalize(s: String): String {
        val lowered = s.trim().lowercase()
        val decomposed = java.text.Normalizer.normalize(lowered, java.text.Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }
}
