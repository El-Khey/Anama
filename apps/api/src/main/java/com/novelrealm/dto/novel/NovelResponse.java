package com.novelrealm.dto.novel;

import java.time.Instant;

import com.novelrealm.model.Novel.NovelStatus;

public record NovelResponse (Long id, String title, String author, String description, String coverImageUrl, NovelStatus status, Instant createdAt) {}
