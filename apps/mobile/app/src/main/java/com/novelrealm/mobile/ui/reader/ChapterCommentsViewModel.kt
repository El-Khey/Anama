package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Nombre de fils affichés d'un coup sous le chapitre, et à chaque « voir plus ». */
private const val PageSize = 6

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
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
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

    private val _state = MutableStateFlow(ChapterCommentsUiState())
    val state: StateFlow<ChapterCommentsUiState> = _state.asStateFlow()

    private var nextPage = 0

    init {
        // Le lecteur instancie ce ViewModel avant même de savoir quel chapitre il
        // affiche ; l'id vaut alors 0 et il n'y a rien à demander.
        if (chapterId > 0) load() else _state.update { it.copy(isLoading = false) }
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

    fun setDraft(draft: String) = _state.update { it.copy(draft = draft, actionError = null) }

    /** Ouvre la rédaction sur un nouveau fil. */
    fun startNewThread() {
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.NewThread,
                draft = "",
                actionError = null,
            )
        }
    }

    /**
     * Prépare une réponse. Répondre à une réponse reste dans le même fil : le pseudo
     * visé est simplement préfixé au message, comme le prévoit #41.
     */
    fun startReply(rootId: Long, toPseudo: String?, mention: Boolean) {
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.Reply(rootId, toPseudo),
                draft = if (mention && !toPseudo.isNullOrBlank()) "@$toPseudo " else "",
                actionError = null,
            )
        }
    }

    fun startEdit(comment: ChapterCommentDto, rootId: Long?) {
        _state.update {
            it.copy(
                composerOpen = true,
                target = ComposerTarget.Edit(comment, rootId),
                draft = comment.body.orEmpty(),
                actionError = null,
            )
        }
    }

    fun closeComposer() {
        _state.update {
            it.copy(
                composerOpen = false,
                target = ComposerTarget.NewThread,
                draft = "",
                actionError = null,
            )
        }
    }

    fun send() {
        val current = _state.value
        val body = current.draft.trim()
        if (body.isEmpty() || current.isSending) return
        _state.update { it.copy(isSending = true, actionError = null) }

        viewModelScope.launch {
            when (val target = current.target) {
                is ComposerTarget.Edit -> applyEdit(target, body)
                is ComposerTarget.Reply ->
                    applyCreate(body, parentId = target.rootId, rootId = target.rootId)
                ComposerTarget.NewThread -> applyCreate(body, parentId = null, rootId = null)
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

    // ── Interne ───────────────────────────────────────────────────────────────

    private suspend fun applyCreate(body: String, parentId: Long?, rootId: Long?) {
        when (val result = commentRepo.create(chapterId, body, parentId)) {
            is ApiResult.Success -> _state.update { state ->
                val created = result.data
                val threads = if (rootId == null) {
                    // Un nouveau fil se pose en tête : c'est là qu'on le cherche des yeux
                    // juste après l'avoir écrit.
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
                )
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
