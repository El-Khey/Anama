package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.dto.CommentReactionsDto
import com.novelrealm.mobile.data.remote.dto.GifDto
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.data.remote.userMessage
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

/** Nombre de fils affichés d'un coup sous le chapitre, et à chaque « voir plus ». */
private const val PageSize = 6

/** Combien de participants proposer quand on tape « @ » sans rien après. */
private const val MaxParticipants = 8

/**
 * Ce que le rédacteur est en train de faire. Un seul état à la fois : on ne peut pas
 * modifier un message et répondre à un autre en même temps.
 */
sealed interface ComposerTarget {
    /** Nouveau fil. */
    data object NewThread : ComposerTarget

    /** Réponse dans un fil. [rootId] est le message racine auquel elle s'accroche. */
    data class Reply(val rootId: Long, val toPseudo: String?) : ComposerTarget

    /** Modification d'un message existant. */
    data class Edit(val comment: ChapterCommentDto, val rootId: Long?) : ComposerTarget
}

data class ChapterCommentsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val threads: List<ChapterCommentDto> = emptyList(),
    /** Total de messages du chapitre, réponses comprises (compteur de l'en-tête). */
    val total: Long = 0,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** Le panneau de rédaction est ouvert (écriture, réponse ou modification). */
    val composerOpen: Boolean = false,
    val draft: String = "",
    val target: ComposerTarget = ComposerTarget.NewThread,
    val isSending: Boolean = false,
    /** Échec d'une action ponctuelle (envoi, suppression) — n'efface pas la liste. */
    val actionError: String? = null,
    /** Suggestions pour le `@…` en cours de frappe (issue #45, §2). */
    val mentionSuggestions: List<UserSearchDto> = emptyList(),
    /** Le serveur propose-t-il les GIF ? (bouton masqué sinon — issue #45, §5). */
    val gifAvailable: Boolean = false,
    val gifPickerOpen: Boolean = false,
    /** GIF joint au brouillon. Jamais en mode modification : un GIF ne s'édite pas. */
    val attachedGif: GifDto? = null,
) {
    // Un GIF seul suffit — sauf en modification, où seul le texte compte.
    val canSend: Boolean
        get() = !isSending &&
            (draft.isNotBlank() || (attachedGif != null && target !is ComposerTarget.Edit))
}

/**
 * Discussion d'un chapitre (#41), affichée **dans le fil du chapitre** — comme sur le
 * web — et non dans un écran séparé.
 *
 * Les mutations mettent la liste à jour **localement** plutôt que de tout recharger :
 * recharger renverrait le lecteur en haut de page juste après avoir répondu. Les règles
 * appliquées ici sont celles du serveur — un message supprimé qui porte des réponses
 * reste en pierre tombale, sans réponse il disparaît.
 */
class ChapterCommentsViewModel(private val chapterId: Long) : ViewModel() {

    private val commentRepo = ServiceLocator.commentRepository
    private val userRepo = ServiceLocator.userRepository
    private val gifRepo = ServiceLocator.gifRepository

    private val _state = MutableStateFlow(ChapterCommentsUiState())
    val state: StateFlow<ChapterCommentsUiState> = _state.asStateFlow()

    private var nextPage = 0

    /**
     * Mentions choisies dans l'autocomplétion : id → pseudo tel qu'inséré. À
     * l'envoi, seules celles dont le « @pseudo » figure ENCORE dans le texte
     * partent — effacer la mention du texte doit suffire à la retirer.
     */
    private val pickedMentions = mutableMapOf<Long, String>()
    private var mentionSearchJob: Job? = null

    init {
        // Le lecteur instancie ce ViewModel avant même de savoir quel chapitre il
        // affiche ; l'id vaut alors 0 et il n'y a rien à demander.
        if (chapterId > 0) load() else _state.update { it.copy(isLoading = false) }
        viewModelScope.launch {
            val available = gifRepo.isAvailable()
            _state.update { it.copy(gifAvailable = available) }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        nextPage = 0
        viewModelScope.launch {
            when (val page = commentRepo.getComments(chapterId, page = 0, size = PageSize)) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            threads = page.data.content,
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                    refreshTotal()
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = page.userMessage())
                }
            }
        }
    }

    /**
     * Charge les six fils suivants. Déclenché par un bouton et non par le défilement :
     * la discussion vit dans la page du chapitre, qui n'est pas une liste paresseuse —
     * et surtout, personne ne veut d'un bas de page qui s'allonge tout seul.
     */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val page = commentRepo.getComments(chapterId, page = nextPage, size = PageSize)) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            // Les fils peuvent bouger entre deux pages : sans ce
                            // dédoublonnage, un même message s'afficherait deux fois.
                            threads = (it.threads + page.data.content).distinctBy { c -> c.id },
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingMore = false, actionError = page.userMessage())
                }
            }
        }
    }

    fun setDraft(draft: String) {
        _state.update { it.copy(draft = draft, actionError = null) }
        refreshMentionSuggestions(draft)
    }

    /** Ouvre la rédaction sur un nouveau fil. */
    fun startNewThread() {
        pickedMentions.clear()
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.NewThread,
                draft = "",
                actionError = null,
                mentionSuggestions = emptyList(),
                attachedGif = null,
            )
        }
    }

    /**
     * Prépare une réponse. Répondre à une réponse reste dans le même fil : le pseudo
     * visé est préfixé au message en VRAIE mention (issue #45, §2) — la personne
     * visée sera donc notifiée, en plus de l'auteur du fil.
     */
    fun startReply(rootId: Long, toUserId: Long?, toPseudo: String?, mention: Boolean) {
        pickedMentions.clear()
        val prefix = if (mention && !toPseudo.isNullOrBlank()) "@$toPseudo " else ""
        if (prefix.isNotEmpty() && toUserId != null) {
            pickedMentions[toUserId] = toPseudo!!
        }
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.Reply(rootId, toPseudo),
                draft = prefix,
                actionError = null,
                mentionSuggestions = emptyList(),
                attachedGif = null,
            )
        }
    }

    fun startEdit(comment: ChapterCommentDto, rootId: Long?) {
        pickedMentions.clear()
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.Edit(comment, rootId),
                draft = comment.body.orEmpty(),
                actionError = null,
                mentionSuggestions = emptyList(),
                attachedGif = null,
            )
        }
    }

    fun closeComposer() {
        pickedMentions.clear()
        mentionSearchJob?.cancel()
        _state.update {
            it.copy(
                composerOpen = false,
                target = ComposerTarget.NewThread,
                draft = "",
                actionError = null,
                mentionSuggestions = emptyList(),
                gifPickerOpen = false,
                attachedGif = null,
            )
        }
    }

    // ── Mentions (issue #45, §2) ──────────────────────────────────────────────

    /** Insère le pseudo choisi à la place du `@…` en cours, et retient QUI il désigne. */
    fun pickMention(user: UserSearchDto) {
        pickedMentions[user.id] = user.pseudo
        _state.update {
            it.copy(
                draft = applyMention(it.draft, user.pseudo),
                mentionSuggestions = emptyList(),
            )
        }
    }

    /**
     * Recherche débouncée sur le jeton `@…` en fin de brouillon. Une frappe annule
     * la recherche précédente : seule la dernière saisie compte, et le serveur ne
     * reçoit pas une requête par lettre.
     */
    private fun refreshMentionSuggestions(draft: String) {
        mentionSearchJob?.cancel()
        val query = activeMentionQuery(draft)
        if (query == null) {
            _state.update { it.copy(mentionSuggestions = emptyList()) }
            return
        }
        // « @ » seul, rien de tapé encore : proposer les gens DÉJÀ dans la
        // discussion. Sans ça, la rangée restait vide jusqu'à ce qu'on devine une
        // lettre du bon pseudo — et une fonctionnalité qui ne se montre pas au
        // premier geste passe pour absente. C'est aussi le cas courant : on
        // mentionne quelqu'un à qui on est en train de parler.
        if (query.isBlank()) {
            _state.update { it.copy(mentionSuggestions = discussionParticipants()) }
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

    /**
     * Les auteurs présents dans la discussion chargée, soi-même exclu (on ne se
     * mentionne pas) et les pierres tombales aussi. Purement local : aucune
     * requête pour le geste le plus fréquent.
     */
    private fun discussionParticipants(): List<UserSearchDto> =
        _state.value.threads
            .flatMap { thread -> listOf(thread) + thread.replies }
            .filter { !it.mine && !it.deleted && it.userId != null && !it.pseudo.isNullOrBlank() }
            .distinctBy { it.userId }
            .take(MaxParticipants)
            .map { UserSearchDto(id = it.userId!!, pseudo = it.pseudo!!, avatarUrl = it.avatarUrl) }

    // ── GIF (issue #45, §5) ───────────────────────────────────────────────────

    fun openGifPicker() = _state.update { it.copy(gifPickerOpen = true) }

    fun closeGifPicker() = _state.update { it.copy(gifPickerOpen = false) }

    fun attachGif(gif: GifDto) =
        _state.update { it.copy(attachedGif = gif, gifPickerOpen = false) }

    fun removeGif() = _state.update { it.copy(attachedGif = null) }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val body = current.draft.trim()
        _state.update { it.copy(isSending = true, actionError = null) }

        viewModelScope.launch {
            when (val target = current.target) {
                is ComposerTarget.Edit -> applyEdit(target, body)
                is ComposerTarget.Reply ->
                    applyCreate(current, body, parentId = target.rootId, rootId = target.rootId)
                ComposerTarget.NewThread ->
                    applyCreate(current, body, parentId = null, rootId = null)
            }
        }
    }

    fun delete(comment: ChapterCommentDto, rootId: Long?) {
        viewModelScope.launch {
            when (val result = commentRepo.delete(comment.id)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        threads = state.threads.removeComment(comment.id, rootId),
                        total = (state.total - 1).coerceAtLeast(0),
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(actionError = result.userMessage())
                }
            }
        }
    }

    /**
     * Pose ou retire une réaction emoji sur un message. Le serveur tranche entre
     * ajouter et retirer selon l'état ; on n'anticipe pas l'affichage, on applique sa
     * réponse (les compteurs et « mes » emojis exacts) au message concerné. Une réaction
     * n'est pas assez lourde pour justifier un affichage optimiste puis un rollback.
     */
    fun react(commentId: Long, emoji: String) {
        viewModelScope.launch {
            when (val result = commentRepo.react(commentId, emoji)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(threads = state.threads.applyReactions(result.data))
                }
                is ApiResult.Error -> _state.update {
                    it.copy(actionError = result.userMessage())
                }
            }
        }
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    private suspend fun applyCreate(
        snapshot: ChapterCommentsUiState,
        body: String,
        parentId: Long?,
        rootId: Long?,
    ) {
        // Seules les mentions encore PRÉSENTES dans le texte partent : en effacer
        // une du brouillon suffit à la retirer. Le serveur refait la même
        // vérification de son côté.
        val mentionedUserIds = pickedMentions
            .filterValues { pseudo -> body.contains("@$pseudo") }
            .keys.toList()

        val result = commentRepo.create(
            chapterId = chapterId,
            body = body,
            parentId = parentId,
            mentionedUserIds = mentionedUserIds,
            gifUrl = snapshot.attachedGif?.url,
            gifPreviewUrl = snapshot.attachedGif?.previewUrl,
        )
        when (result) {
            is ApiResult.Success -> {
                pickedMentions.clear()
                _state.update { state ->
                    val created = result.data
                    val threads = if (rootId == null) {
                        // Un nouveau fil se pose en tête : c'est là qu'on le cherche des
                        // yeux juste après l'avoir écrit.
                        listOf(created) + state.threads
                    } else {
                        state.threads.map { thread ->
                            if (thread.id == rootId) {
                                thread.copy(replies = thread.replies + created)
                            } else {
                                thread
                            }
                        }
                    }
                    state.copy(
                        isSending = false,
                        composerOpen = false,
                        draft = "",
                        target = ComposerTarget.NewThread,
                        threads = threads,
                        total = state.total + 1,
                        mentionSuggestions = emptyList(),
                        attachedGif = null,
                        gifPickerOpen = false,
                    )
                }
            }
            is ApiResult.Error -> _state.update {
                // Le panneau reste ouvert : le texte n'est pas perdu, on peut réessayer.
                it.copy(isSending = false, actionError = result.userMessage())
            }
        }
    }

    private suspend fun applyEdit(target: ComposerTarget.Edit, body: String) {
        when (val result = commentRepo.update(target.comment.id, body)) {
            is ApiResult.Success -> _state.update { state ->
                state.copy(
                    isSending = false,
                    composerOpen = false,
                    draft = "",
                    target = ComposerTarget.NewThread,
                    threads = state.threads.replaceComment(result.data, target.rootId),
                )
            }
            is ApiResult.Error -> _state.update {
                it.copy(isSending = false, actionError = result.userMessage())
            }
        }
    }

    /** Recompte côté serveur : le total inclut les réponses, pas seulement les fils. */
    private fun refreshTotal() {
        viewModelScope.launch {
            (commentRepo.getCount(chapterId) as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(total = result.data) }
            }
        }
    }
}

// ── Mises à jour locales de l'arbre (mêmes règles que le serveur) ─────────────

/**
 * Retire un message. Un fil supprimé qui porte encore des réponses devient une pierre
 * tombale plutôt que de disparaître : sinon ses réponses partiraient avec lui, ce que
 * la suppression douce évite précisément côté base.
 */
private fun List<ChapterCommentDto>.removeComment(
    commentId: Long,
    rootId: Long?,
): List<ChapterCommentDto> {
    if (rootId != null && rootId != commentId) {
        return map { thread ->
            if (thread.id == rootId) {
                thread.copy(replies = thread.replies.filterNot { it.id == commentId })
            } else {
                thread
            }
        }
    }
    return mapNotNull { thread ->
        when {
            thread.id != commentId -> thread
            thread.replies.isEmpty() -> null
            else -> thread.copy(
                body = null,
                deleted = true,
                mine = false,
                pseudo = null,
                avatarUrl = null,
            )
        }
    }
}

/** Remplace un message par sa version modifiée, qu'il soit fil ou réponse. */
private fun List<ChapterCommentDto>.replaceComment(
    updated: ChapterCommentDto,
    rootId: Long?,
): List<ChapterCommentDto> = map { thread ->
    when {
        thread.id == updated.id -> updated.copy(replies = thread.replies)
        thread.id == rootId -> thread.copy(
            replies = thread.replies.map { if (it.id == updated.id) updated else it },
        )
        else -> thread
    }
}

/**
 * Recopie les réactions renvoyées par le serveur sur le message visé — racine ou
 * réponse, cherché dans tout l'arbre. La barre de réaction ne connaît que l'id du
 * message, jamais sa racine : on balaie donc les deux niveaux.
 */
private fun List<ChapterCommentDto>.applyReactions(
    updated: CommentReactionsDto,
): List<ChapterCommentDto> = map { thread ->
    when {
        thread.id == updated.commentId ->
            thread.copy(reactions = updated.reactions, myReactions = updated.myReactions)
        else -> thread.copy(
            replies = thread.replies.map { reply ->
                if (reply.id == updated.commentId) {
                    reply.copy(reactions = updated.reactions, myReactions = updated.myReactions)
                } else {
                    reply
                }
            },
        )
    }
}
