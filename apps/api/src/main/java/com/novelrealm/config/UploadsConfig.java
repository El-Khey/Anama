package com.novelrealm.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sert le dossier d'upload (avatars, bannières) en statique sous
 * {@code /uploads/**} — la stratégie simple de l'issue #17. L'accès est public
 * (cf. {@code SecurityConfig}) : les noms de fichiers contiennent un UUID.
 */
@Configuration
public class UploadsConfig implements WebMvcConfigurer {

    private final Path root;

    public UploadsConfig(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root + "/")
                .setCachePeriod(3600);
    }
}
