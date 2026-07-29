package com.novelrealm.mobile.ui.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// Fabrique minimaliste pour instancier un ViewModel À PARAMÈTRES (DI manuelle, sans Hilt).
// Usage : viewModel(factory = vmFactory { NovelDetailViewModel(novelId) })
fun <VM : ViewModel> vmFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
