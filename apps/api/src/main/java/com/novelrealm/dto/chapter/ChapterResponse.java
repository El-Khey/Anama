package com.novelrealm.dto.chapter;

// Version "liste" : sans le contenu (qui peut être volumineux).
public record ChapterResponse(Long id, Long novelId, Integer chapterNumber, String title) {
}
