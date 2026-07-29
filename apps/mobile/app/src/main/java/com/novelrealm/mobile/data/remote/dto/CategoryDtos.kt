package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `CategoryDetailResponse` (back) : une étagère personnelle et ses romans.
@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val novels: List<NovelDto> = emptyList(),
)

// Corps de `POST /api/categories` et `PATCH /api/categories/{id}` (créer / renommer).
@Serializable
data class CategoryNameRequestDto(
    val name: String,
)

// Corps de `POST /api/categories/{id}/novels`.
@Serializable
data class AddNovelToCategoryRequestDto(
    val novelId: Long,
)
