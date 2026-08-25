package com.novelrealm.service.chapter;

import org.springframework.stereotype.Service;
import java.util.List;

import com.novelrealm.dto.chapter.NovelChapterCount;
import com.novelrealm.exception.chapter.ChapterNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.repository.ChapterRepository;

@Service
public class ChapterService {
    private final ChapterRepository chapterRepository;

    public ChapterService(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    public List<Chapter> findAllByNovelId(Long novelId) {
        return this.chapterRepository.findByNovelIdOrderByChapterNumber(novelId);
    }

    /** Chapitres existants parmi une liste d'ids (les ids inconnus sont ignorés). */
    public List<Chapter> findAllByIds(List<Long> ids) {
        return this.chapterRepository.findAllById(ids);
    }

    public Chapter findById(Long id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> new ChapterNotFoundException(id));
    }

    /** Nombre total de chapitres par roman (pour le résumé de progression). */
    public List<NovelChapterCount> countChaptersPerNovel() {
        return chapterRepository.countChaptersPerNovel();
    }
}
