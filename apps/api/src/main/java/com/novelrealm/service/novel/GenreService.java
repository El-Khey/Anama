package com.novelrealm.service.novel;

import org.springframework.stereotype.Service;
import java.util.List;

import com.novelrealm.model.Genre;
import com.novelrealm.repository.GenreRepository;

@Service
public class GenreService {
    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    public List<Genre> findAll() {
        return this.genreRepository.findAll();
    }
}
