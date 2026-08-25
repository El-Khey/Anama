package com.novelrealm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import com.novelrealm.dto.novel.GenreResponse;
import com.novelrealm.service.novel.GenreService;

@RestController
@RequestMapping("/api/genres")
public class GenreController {
    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @GetMapping
    public List<GenreResponse> findAll() {
        return genreService.findAll().stream()
                .map(genre -> new GenreResponse(genre.getId(), genre.getName()))
                .toList();
    }
}
