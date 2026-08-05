package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.BlockActivityDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.data.repository.PassageRepository
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Erreur d'une action ponctuelle — n'efface jamais ce qui est déjà affiché. */
    val error: String? = null,
) {
    val canSend: Boolean
        get() = !isSending && draft.isNotBlank() &&
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

    private val _state = MutableStateFlow(PassageSocialUiState())
    val state: StateFlow<PassageSocialUiState> = _state.asStateFlow()

    init {
        if (chapterId > 0) loadActivity()
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
     * Pose, remplace ou retire l'emoji — le serveur tranche, on affiche sa réponse.
     *
     * <p>Le panneau **reste ouvert** : on vient d'y arriver pour lire la discussion, se
     * le voir fermer parce qu'on a touché un emoji serait une punition.
     */
    fun react(emoji: String) {
        val blockIndex = _state.value.threadBlock ?: return
        viewModelScope.launch {
            when (val result = passageRepo.react(chapterId, blockIndex, emoji)) {
                is ApiResult.Success -> _state.update { current ->
                    val updated = result.data
                    val previous = current.activity[blockIndex]
                    val merged = (previous ?: BlockActivityDto(blockIndex = blockIndex)).copy(
                        blockIndex = blockIndex,
                        reactions = updated.reactions,
                        myEmoji = updated.myEmoji,
                    )
                    // Un bloc sans réaction NI commentaire ne porte plus de marque :
                    // le retirer de la table évite d'afficher une pastille vide.
                    val activity =
                        if (merged.reactions.isEmpty() && merged.commentCount == 0L) {
                            current.activity - blockIndex
                        } else {
                            current.activity + (blockIndex to merged)
                        }
                    current.copy(activity = activity)
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

    fun closeThread() = _state.update {
        it.copy(threadBlock = null, thread = emptyList(), draft = "", spoiler = false)
    }

    // ── Rédaction ─────────────────────────────────────────────────────────────

    fun setDraft(draft: String) = _state.update { it.copy(draft = draft) }

    fun toggleSpoiler() = _state.update { it.copy(spoiler = !it.spoiler) }

    fun send() {
        val current = _state.value
        val blockIndex = current.threadBlock ?: return
        if (!current.canSend) return

        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = passageRepo.comment(
                chapterId = chapterId,
                blockIndex = blockIndex,
                body = current.draft.trim(),
                spoiler = current.spoiler,
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isSending = false,
                        draft = "",
                        spoiler = false,
                        // Ajouté en fin de fil : il est trié du plus ancien au plus
                        // récent, le nouveau message est donc bien le dernier.
                        thread = it.thread + result.data,
                        activity = it.activity.bump(blockIndex, by = 1),
                    )
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
                is ApiResult.Success -> _state.update {
                    it.copy(
                        thread = it.thread.filterNot { row -> row.id == comment.id },
                        activity = it.activity.bump(blockIndex, by = -1),
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.userMessage()) }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }

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
