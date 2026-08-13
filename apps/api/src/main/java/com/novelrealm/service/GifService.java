package com.novelrealm.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.novelrealm.dto.GifPageResponse;
import com.novelrealm.dto.GifResponse;
import com.novelrealm.exception.GifUnavailableException;
import com.novelrealm.exception.InvalidCommentException;

import tools.jackson.databind.JsonNode;

/**
 * Recherche de GIF via <b>Tenor</b> (issue #45, §5), en proxy : l'app ne parle
 * jamais à Tenor, la clé d'API ne quitte jamais le serveur, et le quota est
 * naturellement protégé par l'authentification de nos propres endpoints.
 *
 * <p><b>Sans clé, la fonctionnalité s'éteint proprement.</b> {@code TENOR_API_KEY}
 * absent du {@code .env} → {@link #isAvailable()} répond {@code false}, l'app
 * masque le bouton GIF, et tout le reste de l'application vit sa vie. Aucune
 * installation n'est cassée par une clé manquante.
 *
 * <p><b>Format servi : « tinygif »</b> (~220 px de large) — un fil de
 * commentaires n'a pas besoin du GIF pleine taille, et les données mobiles non
 * plus. L'image FIGÉE ({@code tinygifpreview}) accompagne chaque résultat :
 * c'est elle que l'app affiche par défaut, l'animation ne démarrant qu'au
 * toucher.
 */
@Service
public class GifService {

    /** Bornes de pagination — Tenor accepte 50 max par page. */
    private static final int MAX_LIMIT = 50;

    /**
     * Délais volontairement courts : Tenor est un agrément, pas une dépendance
     * vitale. Un serveur distant qui ne répond plus ne doit pas retenir un thread
     * Tomcat — le sélecteur affiche « indisponible » et l'app continue.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestClient restClient;
    private final String apiKey;

    public GifService(
            @Value("${app.tenor.api-key}") String apiKey,
            @Value("${app.tenor.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeBoundedRequestFactory())
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    /**
     * Le client HTTP est construit ici, et non injecté.
     *
     * <p>Spring Boot 4 a éclaté ses auto-configurations en modules : le bean
     * {@code RestClient.Builder} tout prêt vient désormais de
     * {@code spring-boot-restclient}, qui n'est PAS tiré par
     * {@code spring-boot-starter-web}. L'injecter faisait échouer le démarrage de
     * TOUTE l'application (« No qualifying bean of type RestClient$Builder ») —
     * un service accessoire ne doit jamais pouvoir empêcher l'API de démarrer.
     * {@link RestClient#builder()} est, lui, une fabrique de {@code spring-web} :
     * toujours là, et elle laisse fixer explicitement les délais ci-dessous, ce
     * que le builder auto-configuré n'aurait pas fait sans réglage.
     */
    private static ClientHttpRequestFactory timeBoundedRequestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /** La clé est-elle configurée ? (L'app masque le bouton GIF si non.) */
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    /** Recherche plein texte. {@code pos} = curseur de la page précédente, ou null. */
    public GifPageResponse search(String query, int limit, String pos) {
        return fetch("/search", query, limit, pos);
    }

    /**
     * Les GIF du moment — ce que montre le sélecteur avant toute saisie. Un
     * sélecteur vide qui attend qu'on tape est un sélecteur qu'on referme.
     */
    public GifPageResponse featured(int limit, String pos) {
        return fetch("/featured", null, limit, pos);
    }

    // ── Validation des URL stockées ───────────────────────────────────────────

    /**
     * Valide une URL de GIF envoyée par un client avant de la mettre en base :
     * HTTPS obligatoire, hôte Tenor obligatoire ({@code media.tenor.com},
     * {@code c.tenor.com}…). On stocke des URL que d'autres lecteurs chargeront —
     * accepter n'importe quel hôte ferait de chaque commentaire un vecteur de
     * traçage (ou pire) vers un serveur arbitraire.
     *
     * @return l'URL inchangée, ou {@code null} si {@code null} en entrée.
     * @throws InvalidCommentException si l'URL ne vient pas de Tenor.
     */
    public static String requireTenorUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCommentException("URL de GIF invalide");
        }
        String host = uri.getHost();
        boolean tenorHost = host != null
                && (host.equals("tenor.com") || host.endsWith(".tenor.com"));
        if (!"https".equals(uri.getScheme()) || !tenorHost) {
            throw new InvalidCommentException("Seuls les GIF Tenor sont acceptés");
        }
        return url.strip();
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    private GifPageResponse fetch(String path, String query, int limit, String pos) {
        if (!isAvailable()) {
            throw new GifUnavailableException(
                    "Les GIF ne sont pas configurés sur ce serveur (clé Tenor absente)");
        }

        int clamped = Math.clamp(limit, 1, MAX_LIMIT);
        JsonNode root;
        try {
            root = restClient.get()
                    .uri(builder -> buildUri(builder, path, query, clamped, pos))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            // Tenor injoignable, quota épuisé, clé révoquée… L'utilisateur n'y peut
            // rien : un 503 franc avec un message lisible, pas une stacktrace.
            throw new GifUnavailableException("La recherche de GIF est indisponible pour le moment");
        }
        if (root == null) {
            throw new GifUnavailableException("La recherche de GIF est indisponible pour le moment");
        }

        List<GifResponse> results = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            JsonNode animated = item.path("media_formats").path("tinygif");
            JsonNode still = item.path("media_formats").path("tinygifpreview");
            String url = animated.path("url").asString("");
            if (url.isEmpty()) {
                continue; // pas de version tinygif : résultat inutilisable, on saute
            }
            JsonNode dims = animated.path("dims");
            results.add(new GifResponse(
                    item.path("id").asString(""),
                    url,
                    // Pas d'image figée fournie → on retombe sur l'animée : moins
                    // sobre, mais jamais une case vide.
                    still.path("url").asString(url),
                    dims.size() > 0 ? dims.get(0).asInt(0) : 0,
                    dims.size() > 1 ? dims.get(1).asInt(0) : 0));
        }
        return new GifPageResponse(results, root.path("next").asString(""));
    }

    private URI buildUri(UriBuilder builder, String path, String query, int limit, String pos) {
        builder.path(path)
                .queryParam("key", apiKey)
                .queryParam("limit", limit)
                // Les deux formats dont l'app a besoin, et rien d'autre : la réponse
                // de Tenor passe de ~40 Ko à quelques Ko par page.
                .queryParam("media_filter", "tinygif,tinygifpreview")
                // Filtre de contenu Tenor (G/PG/PG-13/R). « medium » = PG : le
                // catalogue reste large sans tomber dans ce qu'on ne veut pas voir
                // arriver dans un fil de lecture — la modération fine relève de #42.
                .queryParam("contentfilter", "medium")
                .queryParam("locale", "fr_FR");
        if (query != null && !query.isBlank()) {
            builder.queryParam("q", query.strip());
        }
        if (pos != null && !pos.isBlank()) {
            builder.queryParam("pos", pos.strip());
        }
        return builder.build();
    }
}
