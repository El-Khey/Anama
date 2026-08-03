package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChapterQuoteUiState(
    /** Bloc en cours de citation ; `null` = aucun panneau ouvert. */
    val blockIndex: Int? = null,
    val blockText: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Message de confirmation à afficher brièvement après un enregistrement. */
    val confirmation: String? = null,
)

/**
 * Création d'une citation depuis le lecteur (#41, §3).
 *
 * <p>Volontairement séparé de {@link ChapterCommentsViewModel} : citer est un geste
 * solitaire et privé, commenter est public. Les mêler dans un seul état obligerait
 * chacun à connaître les règles de l'autre.
 */
class ChapterQuoteViewModel(private val chapterId: Long) : ViewModel() {

    private val quoteRepo = ServiceLocator.quoteRepository

    private val _state = MutableStateFlow(ChapterQuoteUiState())
    val state: StateFlow<ChapterQuoteUiState> = _state.asStateFlow()

    /** Ouvre le panneau sur un bloc du chapitre (appui long sur un paragraphe). */
    fun start(blockIndex: Int, blockText: String) {
        _state.value = ChapterQuoteUiState(blockIndex = blockIndex, blockText = blockText)
    }

    fun cancel() {
        _state.value = ChapterQuoteUiState()
    }

    /** Efface la confirmation une fois qu'elle a été montrée. */
    fun confirmationShown() = _state.update { it.copy(confirmation = null) }

    /**
     * Enregistre la citation. On n'envoie que des coordonnées : c'est le serveur qui
     * extrait le texte du chapitre, donc une citation ne peut pas contenir autre
     * chose que ce qui y est réellement écrit.
     */
    fun save(startOffset: Int, endOffset: Int) {
        val blockIndex = _state.value.blockIndex ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            when (val result = quoteRepo.create(chapterId, blockIndex, startOffset, endOffset)) {
                is ApiResult.Success -> _state.value = ChapterQuoteUiState(
                    confirmation = "Citation ajoutée à ta collection",
                )
                is ApiResult.Error -> _state.update {
                    // Le panneau reste ouvert : la sélection patiemment ajustée ne doit
                    // pas être perdue parce que le réseau a hoqueté.
                    it.copy(isSaving = false, error = result.userMessage())
                }
            }
        }
    }
}
