package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `LibraryEntryResponse` (back). `status` : PLAN_TO_READ | READING | COMPLETED | PAUSED.
@Serializable
data class LibraryEntryDto(
    val novel: NovelDto,
    val status: String = "PLAN_TO_READ",
    val addedAt: String? = null,
)

// Corps de `POST /api/library` (status omis → PLAN_TO_READ côté back).
@Serializable
data class AddLibraryEntryRequestDto(
    val novelId: Long,
    val status: String? = null,
)

// Corps de `PATCH /api/library/{novelId}`.
@Serializable
data class UpdateLibraryStatusRequestDto(
    val status: String,
)
