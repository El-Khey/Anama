package com.novelrealm.ingestion.source;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

import com.novelrealm.ingestion.SourceUnavailableException;
import com.novelrealm.ingestion.source.dto.SourceCatalogPage;
import com.novelrealm.ingestion.source.dto.SourceChapterBody;
import com.novelrealm.ingestion.source.dto.SourceChapterPage;
import com.novelrealm.ingestion.source.dto.SourceChapterRef;
import com.novelrealm.ingestion.source.dto.SourceNovel;

/**
 * Adaptateur pour <b>chikari.moe</b>, via son API JSON publique
 * {@code /api/novels/*} (light novels TEXTE ; la famille {@code /api/series/*},
 * qui est de l'image, n'est PAS utilisée). Voir docs/INGESTION_V2.md pour le
 * contrat d'API complet.
 *
 * <p><b>Client HTTP construit ici, pas injecté</b> — même raison que
 * {@code GifService} : Spring Boot 4 a déplacé le bean {@code RestClient.Builder}
 * dans {@code spring-boot-restclient}, non tiré par le starter web. On part donc
 * de {@link RestClient#builder()} (fabrique de {@code spring-web}, toujours
 * présente) et on fixe explicitement les délais.
 *
 * <p><b>Liaison JSON</b> : on bind sur {@link JsonNode} et on mappe à la main
 * vers les DTO neutres — exactement le style de {@code GifService}. Le projet
 * est en Jackson 3 ({@code tools.jackson}) et n'utilise aucun {@code @JsonProperty} ;
 * lire les champs {@code snake_case} au site d'appel évite un second jeu de DTO
 * filaires et tolère les champs inconnus gratuitement.
 *
 * <p><b>Politesse/robustesse</b> : un User-Agent navigateur réel (les UA de bot
 * étaient bloqués sur l'ancien site), des délais courts, et un retry borné à
 * backoff exponentiel sur 429/5xx/timeout. À l'épuisement des tentatives, une
 * {@link SourceUnavailableException} est levée — jamais une stacktrace brute au
 * travers de l'application.
 */
@Component
public class ChikariSourceAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ChikariSourceAdapter.class);

    private static final String SOURCE_KEY = "chikari";

    /** Delais volontairement bornés : un backfill ne doit pas retenir un thread indéfiniment. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final int maxRetries;

    public ChikariSourceAdapter(
            @Value("${novelrealm.ingestion.source.base-url:https://chikari.moe}") String baseUrl,
            @Value("${novelrealm.ingestion.source.user-agent:"
                    + "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36}") String userAgent,
            @Value("${novelrealm.ingestion.max-retries:3}") int maxRetries) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", userAgent)
                .requestFactory(timeBoundedRequestFactory())
                .build();
        this.maxRetries = Math.max(0, maxRetries);
    }

    private static ClientHttpRequestFactory timeBoundedRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    // ── Catalogue ───────────────────────────────────────────────────────────

    @Override
    public SourceCatalogPage listCatalog(String sort, int offset, int limit) {
        JsonNode root = get(b -> {
            b.path("/api/novels").queryParam("offset", offset).queryParam("limit", limit);
            if (sort != null && !sort.isBlank()) {
                b.queryParam("sort", sort.strip());
            }
            return b.build();
        }, "catalogue (offset=" + offset + ")");

        List<SourceNovel> items = new ArrayList<>();
        for (JsonNode it : root.path("items")) {
            items.add(mapCard(it));
        }
        int total = root.path("total").asInt(items.size());
        int off = root.path("offset").asInt(offset);
        int lim = root.path("limit").asInt(limit);
        return new SourceCatalogPage(items, total, off, lim);
    }

    // ── Détail d'un roman ───────────────────────────────────────────────────

    @Override
    public SourceNovel fetchNovel(String slug) {
        JsonNode n = get(b -> b.path("/api/novels/{slug}").build(slug),
                "détail du roman '" + slug + "'");

        List<String> genres = new ArrayList<>();
        for (JsonNode g : n.path("genres")) {
            String name = g.path("name").asString("");
            if (!name.isBlank()) {
                genres.add(name);
            }
        }
        return new SourceNovel(
                n.path("id").asLong(0),
                n.path("slug").asString(slug),
                n.path("title").asString(""),
                pickAuthor(n.path("authors")),
                n.path("description").asString(""),
                blankToNull(n.path("cover_url").asString("")),
                n.path("status").asString(""),
                n.path("is_nsfw").asBoolean(false),
                n.path("chapter_count").asInt(0),
                decimalOrNull(n.path("latest_number")),
                instantOrNull(n.path("last_chapter_at").asString("")),
                genres);
    }

    // ── Liste des chapitres ─────────────────────────────────────────────────

    @Override
    public SourceChapterPage listChapters(String slug, int offset, int limit) {
        JsonNode root = get(b -> b.path("/api/novels/{slug}/chapters")
                .queryParam("offset", offset).queryParam("limit", limit).build(slug),
                "chapitres de '" + slug + "' (offset=" + offset + ")");

        List<SourceChapterRef> items = new ArrayList<>();
        for (JsonNode it : root.path("items")) {
            BigDecimal number = decimalOrNull(it.path("number"));
            if (number == null) {
                continue; // une entrée sans numéro n'est pas exploitable
            }
            items.add(new SourceChapterRef(number, it.path("title").asString("")));
        }
        int total = root.path("total").asInt(items.size());
        int off = root.path("offset").asInt(offset);
        int lim = root.path("limit").asInt(limit);
        return new SourceChapterPage(items, total, off, lim);
    }

    // ── Corps d'un chapitre ─────────────────────────────────────────────────

    @Override
    public SourceChapterBody fetchChapterBody(String slug, BigDecimal number) {
        // Le numéro voyage dans le CHEMIN. On envoie une forme sans zéros
        // superflus (« 12 » et non « 12.00 ») pour matcher l'URL du reader.
        String num = number.stripTrailingZeros().toPlainString();
        JsonNode c = get(b -> b.path("/api/novels/{slug}/chapters/{number}/read").build(slug, num),
                "corps du chapitre " + num + " de '" + slug + "'");

        return new SourceChapterBody(
                decimalOr(c.path("number"), number),
                c.path("title").asString(""),
                c.path("body").asString(""),
                c.path("locked").asBoolean(false));
    }

    // ── Mapping helpers ─────────────────────────────────────────────────────

    /** Carte de catalogue : la liste ne porte pas description/genres (récupérés à l'import). */
    private SourceNovel mapCard(JsonNode it) {
        return new SourceNovel(
                it.path("id").asLong(0),
                it.path("slug").asString(""),
                it.path("title").asString(""),
                "", // auteur non fourni dans la liste
                "", // description non fournie dans la liste
                blankToNull(it.path("cover_url").asString("")),
                it.path("status").asString(""),
                it.path("is_nsfw").asBoolean(false),
                it.path("chapter_count").asInt(0),
                decimalOrNull(it.path("latest_chapter")),
                instantOrNull(it.path("last_chapter_at").asString("")),
                List.of());
    }

    /**
     * chikari fournit {@code authors[]} avec des rôles. On prend le premier
     * {@code role == "author"}, sinon le premier auteur, sinon « Inconnu »
     * (le champ {@code author} est {@code NOT NULL} côté base).
     */
    private static String pickAuthor(JsonNode authors) {
        String firstAny = null;
        for (JsonNode a : authors) {
            String name = a.path("name").asString("");
            if (name.isBlank()) {
                continue;
            }
            if (firstAny == null) {
                firstAny = name;
            }
            if ("author".equalsIgnoreCase(a.path("role").asString(""))) {
                return name;
            }
        }
        return firstAny != null ? firstAny : "Inconnu";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String raw = node.asString("");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal decimalOr(JsonNode node, BigDecimal fallback) {
        BigDecimal v = decimalOrNull(node);
        return v != null ? v : fallback;
    }

    private static Instant instantOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException ex) {
            // chikari renvoie un offset (« +00:00 ») → Instant.parse gère le « Z »
            // mais pas toujours l'offset explicite selon la forme. On retombe via
            // OffsetDateTime pour être robuste.
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    // ── Transport : GET + retry borné ───────────────────────────────────────

    /**
     * GET une URL de la source et renvoie le corps en {@link JsonNode}, avec un
     * retry borné à backoff exponentiel sur 429/5xx et erreurs réseau. À
     * l'épuisement, lève {@link SourceUnavailableException}.
     *
     * @param uriFn  construit l'URI (voyage la pagination / le slug)
     * @param what   description humaine pour les logs et le message d'erreur
     */
    private JsonNode get(java.util.function.Function<UriBuilder, URI> uriFn, String what) {
        int attempt = 0;
        while (true) {
            try {
                JsonNode body = restClient.get()
                        .uri(uriFn::apply)
                        .retrieve()
                        .body(JsonNode.class);
                if (body == null || body.isMissingNode()) {
                    throw new SourceUnavailableException("Réponse vide de la source pour " + what);
                }
                return body;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (isRetryable(status) && attempt < maxRetries) {
                    backoff(attempt, what, status + "");
                    attempt++;
                    continue;
                }
                throw new SourceUnavailableException(
                        "Source a répondu " + status + " pour " + what, ex);
            } catch (SourceUnavailableException ex) {
                throw ex;
            } catch (Exception ex) {
                // Timeouts, DNS, connexion refusée… (ResourceAccessException & co.)
                if (attempt < maxRetries) {
                    backoff(attempt, what, ex.getClass().getSimpleName());
                    attempt++;
                    continue;
                }
                throw new SourceUnavailableException("Source injoignable pour " + what, ex);
            }
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    /** Pause exponentielle : ~1s, 2s, 4s… bornée à 10s. */
    private static void backoff(int attempt, String what, String cause) {
        long millis = Math.min(10_000L, 1_000L * (1L << attempt));
        log.warn("Source instable ({}) sur {} — nouvelle tentative dans {} ms", cause, what, millis);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Interrompu pendant l'attente de retry sur " + what, ie);
        }
    }
}
