package com.novelrealm.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

import com.novelrealm.dto.chapter.ChapterDetailResponse;
import com.novelrealm.dto.chapter.ChapterResponse;
import com.novelrealm.model.Chapter;
import com.novelrealm.service.chapter.ChapterService;

@RestController
@RequestMapping("/api/chapters")
public class ChapterController {
    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    // Liste des chapitres d'un roman (sans contenu), triée par numéro.
    @GetMapping("/novel/{novelId}")
    public List<ChapterResponse> findAllByNovel(@PathVariable Long novelId) {
        return chapterService.findAllByNovelId(novelId).stream()
                .map(chapter -> new ChapterResponse(
                        chapter.getId(),
                        chapter.getNovel().getId(),
                        chapter.getChapterNumber(),
                        chapter.getTitle()))
                .toList();
    }

    // Un chapitre avec son contenu — pour la page de lecture.
    @GetMapping("/{id}")
    public ChapterDetailResponse findById(@PathVariable Long id) {
        Chapter chapter = chapterService.findById(id);
        return new ChapterDetailResponse(
                chapter.getId(),
                chapter.getNovel().getId(),
                chapter.getChapterNumber(),
                chapter.getTitle(),
                chapter.getContent(),
                chapter.getCreatedAt());
    }
}
