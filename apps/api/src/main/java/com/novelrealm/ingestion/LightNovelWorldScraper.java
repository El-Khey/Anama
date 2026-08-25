package com.novelrealm.ingestion;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper LightNovelWorld (Jsoup). Convertit le HTML en records "Scraped*".
 * Aucune dépendance aux entités JPA : pure récupération de données.
 *
 * Sélecteurs basés sur le HTML réel (inspecté). Si la structure du site change,
 * c'est ici qu'il faut adapter — rien d'autre.
 */
@Component
public class LightNovelWorldScraper {

    private static final Logger log = LoggerFactory.getLogger(LightNovelWorldScraper.class);

    private static final String BASE_URL = "https://lightnovelworld.org";
    // UA de navigateur réel : le site redirige (302) les UA "bot" sur les pages
    // de contenu chapitre (contenu "protégé") → sans ça, chapitres vides en base.
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;

    private static final Pattern CHAPTER_NUM = Pattern.compile("/chapter/(\\d+)");
    private static final Pattern PAGE_NUM = Pattern.compile("page=(\\d+)");
    // Les cartes chapitre sont des <div> sans <a> : le lien est dans onclick="location.href='…'".
    private static final Pattern ONCLICK_HREF = Pattern.compile("location\\.href='([^']+)'");
    private static final Pattern GENRE_ARRAY = Pattern.compile("\"genre\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern GENRE_STRING = Pattern.compile("\"genre\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    /** Métadonnées du roman (page /novel/{slug}/). */
    public ScrapedNovel scrapeNovel(String slug) throws IOException {
        Document doc = fetch(BASE_URL + "/novel/" + slug + "/");

        String title = textOr(doc.selectFirst("h1.novel-title"), slug);
        String author = textOr(doc.selectFirst("a.author-link"), "Inconnu");
        String description = textOr(doc.selectFirst(".summary-content"), "");
        String status = textOr(doc.selectFirst(".status-badge"), "Ongoing");
        String cover = doc.select("meta[property=og:image]").attr("content");
        List<String> genres = extractGenres(doc);

        return new ScrapedNovel(slug, title, author, description,
                cover.isBlank() ? null : cover, status, genres);
    }

    /** Liste des chapitres (page /novel/{slug}/chapters/, paginée). */
    public List<ScrapedChapterRef> scrapeChapterRefs(String slug, int maxChapters) throws IOException {
        // Garde : une limite ≤ 0 n'importe RIEN (au lieu de crawler tout le roman,
        // ce qui serait un piège pour une valeur mal configurée). Mettre > 0.
        if (maxChapters <= 0) {
            log.warn("max-chapters={} (≤ 0) → aucun chapitre importé. Réglez une valeur > 0.", maxChapters);
            return List.of();
        }

        List<ScrapedChapterRef> refs = new ArrayList<>();
        String base = BASE_URL + "/novel/" + slug + "/chapters/";

        Document first = fetch(base);
        int lastPage = maxPage(first);

        for (int page = 1; page <= lastPage; page++) {
            Document doc = (page == 1) ? first : fetch(base + "?page=" + page);
            for (Element card : doc.select(".chapter-card")) {
                String href = chapterHref(card);
                if (href == null) {
                    continue;
                }
                Matcher m = CHAPTER_NUM.matcher(href);
                if (!m.find()) {
                    continue;
                }
                int number = Integer.parseInt(m.group(1));
                String title = textOr(card.selectFirst(".chapter-title"), "Chapitre " + number);
                refs.add(new ScrapedChapterRef(number, title, href));
                if (refs.size() >= maxChapters) {
                    return refs;
                }
            }
        }
        return refs;
    }

    /** Contenu d'un chapitre (page /novel/{slug}/chapter/{n}/). */
    public ScrapedChapter scrapeChapter(ScrapedChapterRef ref) throws IOException {
        Document doc = fetch(ref.url());
        String title = textOr(doc.selectFirst(".chapter-title"), ref.title());

        StringBuilder content = new StringBuilder();
        Element body = doc.selectFirst("#chapterText");
        if (body != null) {
            body.select("script, style, .chapter-ad-container").remove();
            for (Element p : body.select("p")) {
                String t = p.text().strip();
                if (!t.isEmpty()) {
                    if (content.length() > 0) {
                        content.append("\n\n");
                    }
                    content.append(t);
                }
            }
        }
        return new ScrapedChapter(ref.chapterNumber(), title, content.toString());
    }

    // ------------------------------------------------------------------ utils

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
    }

    /** URL absolue d'un chapitre depuis sa carte. Soit un vrai <a>, soit — cas
     *  réel de LNW — le path dans l'attribut onclick="location.href='…'". */
    private String chapterHref(Element card) {
        Element link = card.selectFirst("a[href*=/chapter/]");
        if (link != null) {
            return link.absUrl("href");
        }
        Matcher m = ONCLICK_HREF.matcher(card.attr("onclick"));
        if (m.find()) {
            String path = m.group(1);
            return path.startsWith("http") ? path : BASE_URL + path;
        }
        return null;
    }

    /** Numéro de page max d'après les liens de pagination (?page=N). */
    private int maxPage(Document doc) {
        int max = 1;
        for (Element a : doc.select("a[href*=page=]")) {
            Matcher m = PAGE_NUM.matcher(a.attr("href"));
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max;
    }

    /** Genres depuis le JSON-LD (champ "genre"), extraits par regex pour éviter
     *  une dépendance à un parser JSON. Gère le tableau ["A","B"] et la chaîne "A". */
    private List<String> extractGenres(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();

            Matcher array = GENRE_ARRAY.matcher(json);
            if (array.find()) {
                List<String> out = new ArrayList<>();
                Matcher q = QUOTED.matcher(array.group(1));
                while (q.find()) {
                    out.add(q.group(1));
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }

            Matcher single = GENRE_STRING.matcher(json);
            if (single.find()) {
                return List.of(single.group(1));
            }
        }
        return List.of();
    }

    private String textOr(Element el, String fallback) {
        if (el == null) {
            return fallback;
        }
        String t = el.text().strip();
        return t.isEmpty() ? fallback : t;
    }
}
