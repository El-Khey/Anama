package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.BlockActivityDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.CommentVotesDto
import com.novelrealm.mobile.data.remote.dto.GifDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.data.repository.PassageRepository
import com.novelrealm.mobile.di.ServiceLocator
import com.novelrealm.mobile.ui.components.activeMentionQuery
import com.novelrealm.mobile.ui.components.applyMention
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Combien de participants proposer quand on tape « @ » sans rien après. */
private const val MAX_THREAD_PARTICIPANTS = 8

data class PassageSocialUiState(
    /** Activité par index de bloc — la clé est l'index ACTUEL, recalé par le serveur. */
    val activity: Map<Int, BlockActivityDto> = emptyMap(),
    /** Messages dont le passage a disparu du chapitre ; signalés en fin de chapitre. */
    val orphanedComments: Long = 0,
    /** Bloc dont le panneau est ouvert. Un seul état : il n'y a qu'un panneau. */
    val threadBlock: Int? = null,
    val thread: List<PassageCommentDto> = emptyList(),
    val threadLoading: Boolean = false,
    val draft: String = "",
    val spoiler: Boolean = false,
    val isSending: Boolean = false,
    /** Message auquel on répond, ou `null` pour ouvrir un fil. */
    val replyTo: PassageCommentDto? = null,
    /**
     * Emoji dont il faut jouer la pluie, une fois. Consommé par l'écran puis remis à
     * `null` — sans quoi la moindre recomposition la relancerait.
     */
    val celebration: String? = null,
    /** Erreur d'une action ponctuelle — n'efface jamais ce qui est déjà affiché. */
    val error: String? = null,
    /** Suggestions pour le `@…` en cours de frappe (issue #45, §2). */
    val mentionSuggestions: List<UserSearchDto> = emptyList(),
    /** Le serveur propose-t-il les GIF ? (bouton masqué sinon — issue #45, §5). */
    val gifAvailable: Boolean = false,
    val gifPickerOpen: Boolean = false,
    /** GIF joint au brouillon (issue #45, §5) — un message peut être un GIF seul. */
    val attachedGif: GifDto? = null,
    /**
     * Avatar et pseudo du lecteur connecté, pour l'afficher en tête du composeur (façon
     * TikTok). Chargés une fois via `getMe()` — l'app ne les garde pas en mémoire par
     * ailleurs. `null` tant que la réponse n'est pas là : on retombe alors sur l'initiale.
     */
    val myAvatarUrl: String? = null,
    val myPseudo: String? = null,
) {
    val canSend: Boolean
        get() = !isSending && (draft.isNotBlank() || attachedGif != null) &&
            draft.length <= PassageRepository.MAX_BODY_LENGTH
}

/**
 * Réactions et commentaires accrochés aux passages du chapitre ouvert (#41, §4).
 *
 * <p><b>Un seul chargement d'agrégats par chapitre.</b> Les compteurs de tous les blocs
 * arrivent à l'ouverture ; un fil n'est demandé que si le lecteur le déplie vraiment.
 * La plupart des blocs actifs ne le seront jamais.
 *
 * <p><b>Aucune mise à jour optimiste.</b> Une réaction renvoie l'état recalculé du
 * bloc, et c'est cet état qui est affiché. Deviner localement obligerait à rejouer les
 * règles du serveur — un lecteur n'a qu'une réaction par passage, toucher le même
 * emoji le retire — et un désaccord se verrait immédiatement, l'emoji clignotant sous
 * le doigt.
 */
class PassageSocialViewModel(private val chapterId: Long) : ViewModel() {

    private val passageRepo = ServiceLocator.passageRepository
    private val userRepo = ServiceLocator.userRepository
    private val gifRepo = ServiceLocator.gifRepository

    private val _state = MutableStateFlow(PassageSocialUiState())
    val state: StateFlow<PassageSocialUiState> = _state.asStateFlow()

    /** Mentions choisies : id → pseudo inséré. Voir `ChapterCommentsViewModel`. */
    private val pickedMentions = mutableMapOf<Long, String>()
    private var mentionSearchJob: Job? = null

    init {
        if (chapterId > 0) loadActivity()
        viewModelScope.launch {
            val available = gifRepo.isAvailable()
            _state.update { it.copy(gifAvailable = available) }
        }
        // Avatar du lecteur pour le composeur (façon TikTok). Silencieux en cas d'échec :
        // pas d'avatar → on affiche l'initiale, ce n'est pas une erreur à signaler.
        viewModelScope.launch {
            (userRepo.getMe() as? ApiResult.Success)?.let { me ->
                _state.update {
                    it.copy(myAvatarUrl = me.data.avatarUrl, myPseudo = me.data.pseudo)
                }
            }
        }
    }

    /**
     * Les compteurs de tout le chapitre. Silencieux en cas d'échec : le lecteur est là
     * pour lire, et l'absence de marques en marge ne l'empêche de rien.
     */
    fun loadActivity() {
        viewModelScope.launch {
            (passageRepo.activity(chapterId) as? ApiResult.Success)?.let { result ->
                _state.update {
                    it.copy(
                        activity = result.data.blocks.associateBy { block -> block.blockIndex },
                        orphanedComments = result.data.orphanedComments,
                    )
                }
            }
        }
    }

    // ── Réactions ─────────────────────────────────────────────────────────────

    /**
     * Pose ou retire un emoji sur un bloc — multi-emoji façon Discord, le serveur tranche
     * entre ajouter et retirer selon l'état, on applique sa réponse.
     *
     * <p>Ne ferme rien : ce geste vient désormais du double tap sur le paragraphe (barre
     * de réaction rapide), pas de l'ouverture d'un panneau. L'emoji pleut une fois, mais
     * seulement quand on POSE (pas au retrait) — fêter un renoncement n'aurait aucun sens,
     * et enchaîner les retraits ne doit pas déclencher la pluie.
     */
    fun reactToBlock(blockIndex: Int, emoji: String) {
        viewModelScope.launch {
            when (val result = passageRepo.react(chapterId, blockIndex, emoji)) {
                is ApiResult.Success -> _state.update { current ->
                    val updated = result.data
                    // « Posé » = l'emoji est maintenant dans mes réactions alors qu'il n'y
                    // était pas avant. C'est ce qui distingue un ajout d'un retrait.
                    val hadBefore = current.activity[blockIndex]
                        ?.myReactions?.contains(emoji) == true
                    val posted = !hadBefore && updated.myReactions.contains(emoji)

                    val previous = current.activity[blockIndex]
                    val merged = (previous ?: BlockActivityDto(blockIndex = blockIndex)).copy(
                        blockIndex = blockIndex,
                        reactions = updated.reactions,
                        myReactions = updated.myReactions,
                    )
                    // Un bloc sans réaction NI commentaire ne porte plus de marque :
                    // le retirer de la table évite d'afficher une pastille vide.
                    val activity =
                        if (merged.reactions.isEmpty() && merged.commentCount == 0L) {
                            current.activity - blockIndex
                        } else {
                            current.activity + (blockIndex to merged)
                        }
                    current.copy(
                        activity = activity,
                        celebration = if (posted) emoji else current.celebration,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.userMessage()) }
            }
        }
    }

    // ── Fil d'un passage ──────────────────────────────────────────────────────

    fun openThread(blockIndex: Int) {
        _state.update {
            it.copy(
                threadBlock = blockIndex,
                thread = emptyList(),
                threadLoading = true,
                error = null,
            )
        }
        viewModelScope.launch {
            when (val result = passageRepo.thread(chapterId, blockIndex)) {
                is ApiResult.Success ->
                    _state.update { it.copy(threadLoading = false, thread = result.data) }
                is ApiResult.Error -> _state.update {
                    it.copy(threadLoading = false, error = result.userMessage())
                }
            }
        }
    }

    fun closeThread() {
        pickedMentions.clear()
        mentionSearchJob?.cancel()
        _state.update {
            it.copy(
                threadBlock = null,
                thread = emptyList(),
                draft = "",
                spoiler = false,
                replyTo = null,
                mentionSuggestions = emptyList(),
                gifPickerOpen = false,
                attachedGif = null,
            )
        }
    }

    fun celebrationShown() = _state.update { it.copy(celebration = null) }

    // ── Réponses ──────────────────────────────────────────────────────────────

    /**
     * Vise un message. On peut viser une réponse : le serveur re-rattachera au fil
     * racine plutôt que de refuser, parce que du point de vue du lecteur le geste est
     * le même. Viser une RÉPONSE insère la mention de son auteur : c'est elle qui
     * rend le rattachement lisible (issue #45, §6) et qui le notifie (§3).
     */
    fun startReply(comment: PassageCommentDto) {
        _state.update { current ->
            var draft = current.draft
            // Mention automatique quand on répond à une réponse : l'auteur du fil est
            // déjà notifié, celui de la réponse ne le serait pas sans elle.
            if (comment.mine.not() && comment.userId != null && !comment.pseudo.isNullOrBlank() &&
                draft.isEmpty()
            ) {
                pickedMentions[comment.userId] = comment.pseudo
                draft = "@${comment.pseudo} "
            }
            current.copy(replyTo = comment, draft = draft, error = null)
        }
    }

    fun cancelReply() = _state.update { it.copy(replyTo = null) }

    // ── Rédaction ─────────────────────────────────────────────────────────────

    fun setDraft(draft: String) {
        _state.update { it.copy(draft = draft) }
        refreshMentionSuggestions(draft)
    }

    /**
     * Le bouton « @ » : insère un « @ » à la fin du brouillon (précédé d'une espace s'il
     * en manque), exactement comme si on l'avait tapé — les suggestions s'ouvrent alors
     * toutes seules. C'est un raccourci vers un geste qui existe déjà, pas un second
     * mécanisme de mention.
     */
    fun insertMentionTrigger() {
        val base = _state.value.draft
        val needsSpace = base.isNotEmpty() && !base.endsWith(" ") && !base.endsWith("\n")
        val draft = base + (if (needsSpace) " @" else "@")
        setDraft(draft)
    }

    fun toggleSpoiler() = _state.update { it.copy(spoiler = !it.spoiler) }

    // ── Mentions (issue #45, §2) ──────────────────────────────────────────────

    fun pickMention(user: UserSearchDto) {
        pickedMentions[user.id] = user.pseudo
        _state.update {
            it.copy(
                draft = applyMention(it.draft, user.pseudo),
                mentionSuggestions = emptyList(),
            )
        }
    }

    private fun refreshMentionSuggestions(draft: String) {
        mentionSearchJob?.cancel()
        val query = activeMentionQuery(draft)
        if (query == null) {
            _state.update { it.copy(mentionSuggestions = emptyList()) }
            return
        }
        // « @ » seul : les gens du fil ouvert, sans requête. Même règle que la
        // discussion de fin de chapitre — le geste doit répondre pareil partout.
        if (query.isBlank()) {
            _state.update { it.copy(mentionSuggestions = threadParticipants()) }
            return
        }
        mentionSearchJob = viewModelScope.launch {
            delay(250)
            when (val result = userRepo.search(query)) {
                is ApiResult.Success ->
                    _state.update { it.copy(mentionSuggestions = result.data) }
                is ApiResult.Error ->
                    _state.update { it.copy(mentionSuggestions = emptyList()) }
            }
        }
    }

    /** Les auteurs du fil ouvert, soi-même exclu — aucune requête réseau. */
    private fun threadParticipants(): List<UserSearchDto> =
        _state.value.thread
            .flatMap { comment -> listOf(comment) + comment.replies }
            .filter { !it.mine && it.userId != null && !it.pseudo.isNullOrBlank() }
            .distinctBy { it.userId }
            .take(MAX_THREAD_PARTICIPANTS)
            .map { UserSearchDto(id = it.userId!!, pseudo = it.pseudo!!, avatarUrl = it.avatarUrl) }

    // ── GIF (issue #45, §5) ───────────────────────────────────────────────────

    fun openGifPicker() = _state.update { it.copy(gifPickerOpen = true) }

    fun closeGifPicker() = _state.update { it.copy(gifPickerOpen = false) }

    fun attachGif(gif: GifDto) =
        _state.update { it.copy(attachedGif = gif, gifPickerOpen = false) }

    fun removeGif() = _state.update { it.copy(attachedGif = null) }

    fun send() {
        val current = _state.value
        val blockIndex = current.threadBlock ?: return
        if (!current.canSend) return

        val body = current.draft.trim()
        // Voir ChapterCommentsViewModel : seules les mentions encore dans le texte partent.
        val mentionedUserIds = pickedMentions
            .filterValues { pseudo -> body.contains("@$pseudo") }
            .keys.toList()

        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = passageRepo.comment(
                chapterId = chapterId,
                blockIndex = blockIndex,
                body = body,
                spoiler = current.spoiler,
                parentId = current.replyTo?.id,
                mentionedUserIds = mentionedUserIds,
                gifUrl = current.attachedGif?.url,
                gifPreviewUrl = current.attachedGif?.previewUrl,
            )
            when (result) {
                is ApiResult.Success -> {
                    pickedMentions.clear()
                    _state.update {
                        it.copy(
                            isSending = false,
                            draft = "",
                            spoiler = false,
                            replyTo = null,
                            mentionSuggestions = emptyList(),
                            attachedGif = null,
                            gifPickerOpen = false,
                            activity = it.activity.bump(blockIndex, by = 1),
                        )
                    }
                    // Le fil est rechargé plutôt que reconstruit ici. Insérer une
                    // réponse au bon endroit obligerait à rejouer localement la règle
                    // de re-rattachement du serveur — répondre à une réponse remonte
                    // d'un cran — et le moindre écart afficherait un arbre faux.
                    refreshThread(blockIndex)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isSending = false, error = result.userMessage())
                }
            }
        }
    }

    fun delete(comment: PassageCommentDto) {
        val blockIndex = _state.value.threadBlock ?: return
        viewModelScope.launch {
            when (val result = passageRepo.delete(comment.id)) {
                is ApiResult.Success -> {
                    // Supprimer un message racine emporte ses réponses côté serveur :
                    // le compteur baisse d'autant, et seul un rechargement le sait.
                    _state.update {
                        it.copy(
                            activity = it.activity.bump(
                                blockIndex,
                                by = -(1 + comment.replies.size),
                            ),
                        )
                    }
                    refreshThread(blockIndex)
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.userMessage()) }
            }
        }
    }

    /**
     * Pose ou retire une réaction emoji sur un MESSAGE du fil (pas sur le bloc : voir
     * [react]). Le serveur tranche entre ajouter et retirer ; on applique sa réponse
     * — compteurs et « mes » emojis exacts — au message concerné, sans recharger le fil.
     */
    fun reactToComment(annotationId: Long, emoji: String) {
        viewModelScope.launch {
            when (val result = passageRepo.reactToComment(annotationId, emoji)) {
                is ApiResult.Success -> _state.update {
                    it.copy(thread = it.thread.applyReactions(result.data))
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.userMessage()) }
            }
        }
    }

    /**
     * Vote (pouce vert / rouge) sur un message de passage. Même logique que
     * [reactToComment] : le serveur tranche, on applique sa réponse au message visé.
     */
    fun voteComment(annotationId: Long, value: Int) {
        viewModelScope.launch {
            when (val result = passageRepo.voteComment(annotationId, value)) {
                is ApiResult.Success -> _state.update {
                    it.copy(thread = it.thread.applyVotes(result.data))
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.userMessage()) }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

    /** Recharge le fil sans vider l'affichage : on corrige, on ne fait pas clignoter. */
    private fun refreshThread(blockIndex: Int) {
        viewModelScope.launch {
            (passageRepo.thread(chapterId, blockIndex) as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(thread = result.data) }
            }
        }
    }

}

/**
 * Corrige le compteur d'un bloc après un ajout ou une suppression, plutôt que de
 * redemander les agrégats du chapitre entier pour un message.
 */
private fun Map<Int, BlockActivityDto>.bump(blockIndex: Int, by: Int): Map<Int, BlockActivityDto> {
    val previous = this[blockIndex] ?: BlockActivityDto(blockIndex = blockIndex)
    val count = (previous.commentCount + by).coerceAtLeast(0)
    val updated = previous.copy(blockIndex = blockIndex, commentCount = count)
    return if (count == 0L && updated.reactions.isEmpty()) {
        this - blockIndex
    } else {
        this + (blockIndex to updated)
    }
}

/**
 * Recopie les réactions renvoyées par le serveur sur le message visé — racine ou
 * réponse, cherché dans tout le fil. La barre de réaction ne connaît que l'id du
 * message, jamais sa racine : on balaie donc les deux niveaux.
 */
private fun List<PassageCommentDto>.applyReactions(
    updated: CommentReactionsDto,
): List<PassageCommentDto> = map { root ->
    when {
        root.id == updated.commentId ->
            root.copy(reactions = updated.reactions, myReactions = updated.myReactions)
        else -> root.copy(
            replies = root.replies.map { reply ->
                if (reply.id == updated.commentId) {
                    reply.copy(reactions = updated.reactions, myReactions = updated.myReactions)
                } else {
                    reply
                }
            },
        )
    }
}

/**
 * Recopie les votes renvoyés par le serveur sur le message visé — racine ou réponse,
 * cherché dans tout le fil. Même logique que [applyReactions].
 */
private fun List<PassageCommentDto>.applyVotes(
    updated: CommentVotesDto,
): List<PassageCommentDto> = map { root ->
    when {
        root.id == updated.commentId ->
            root.copy(likes = updated.likes, dislikes = updated.dislikes, myVote = updated.myVote)
        else -> root.copy(
            replies = root.replies.map { reply ->
                if (reply.id == updated.commentId) {
                    reply.copy(
                        likes = updated.likes, dislikes = updated.dislikes, myVote = updated.myVote)
                } else {
                    reply
                }
            },
        )
    }
}
