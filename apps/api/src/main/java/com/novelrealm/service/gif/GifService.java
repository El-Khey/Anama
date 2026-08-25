package com.novelrealm.service.gif;

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
import tools.jackson.databind.JsonNode;

import com.novelrealm.dto.gif.GifPageResponse;
import com.novelrealm.dto.gif.GifResponse;
import com.novelrealm.exception.comment.InvalidCommentException;
import com.novelrealm.exception.gif.GifUnavailableException;

/**
 * Recherche de GIF (issue #45, §5), en proxy : l'app ne parle jamais au
 * fournisseur, la clé d'API ne quitte jamais le serveur, et le quota est
 * naturellement protégé par l'authentification de nos propres endpoints.
 *
 * <p><b>Pourquoi KLIPY et pas Tenor.</b> Ce service visait Tenor à l'origine.
 * Google a fermé les inscriptions à l'API Tenor le 13 janvier 2026 puis l'a
 * définitivement arrêtée le 30 juin 2026 — tenter d'activer
 * {@code tenor.googleapis.com} sur un projet Google Cloud répond désormais
 * {@code PERMISSION_DENIED}. KLIPY est le remplaçant vers lequel les mêmes
 * applications ont migré ; son catalogue et sa forme d'API sont équivalents.
 *
 * <p><b>Sans clé, la fonctionnalité s'éteint proprement.</b> {@code KLIPY_API_KEY}
 * absent du {@code .env} → {@link #isAvailable()} répond {@code false}, l'app
 * masque le bouton GIF, et tout le reste de l'application vit sa vie. Aucune
 * installation n'est cassée par une clé manquante.
 *
 * <p><b>Format servi : la taille « sm »</b> (~220 px de large) — un fil de
 * commentaires n'a pas besoin du GIF pleine taille, et les données mobiles non
 * plus. KLIPY fournit en prime un <b>vrai JPEG figé</b> de la même image
 * ({@code sm.jpg}, ~8 Ko) : c'est lui que l'app affiche par défaut, l'animation
 * ne démarrant qu'au toucher. Un GIF animé de 300 Ko n'est donc téléchargé que
 * si quelqu'un le demande vraiment.
 */
@Service
public class GifService {

    /** Bornes de pagination — KLIPY accepte 8 à 50 éléments par page. */
    private static final int MIN_LIMIT = 8;
    private static final int MAX_LIMIT = 50;

    /**
     * Formats demandés, pour alléger la réponse : sans ce filtre, KLIPY renvoie
     * aussi webp, mp4 et webm en quatre tailles — soit vingt URL par résultat
     * dont on n'utilise que deux.
     */
    private static final String FORMATS = "gif,jpg";

    /**
     * Filtre de contenu KLIPY ({@code off|low|medium|high}). « medium » garde un
     * catalogue large sans laisser arriver n'importe quoi dans un fil de lecture —
     * la modération fine relève de #42.
     */
    private static final String CONTENT_FILTER = "medium";

    /**
     * Délais volontairement courts : la recherche de GIF est un agrément, pas une
     * dépendance vitale. Un serveur distant qui ne répond plus ne doit pas retenir
     * un thread Tomcat — le sélecteur affiche « indisponible » et l'app continue.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(8);

    private final RestClient restClient;
    private final String apiKey;

    public GifService(
            @Value("${app.klipy.api-key}") String apiKey,
            @Value("${app.klipy.base-url}") String baseUrl) {
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

    /** Recherche plein texte. {@code pos} = curseur rendu par la page précédente. */
    public GifPageResponse search(String query, int limit, String pos) {
        return fetch("search", query, limit, pos);
    }

    /**
     * Les GIF du moment — ce que montre le sélecteur avant toute saisie. Un
     * sélecteur vide qui attend qu'on tape est un sélecteur qu'on referme.
     */
    public GifPageResponse featured(int limit, String pos) {
        return fetch("trending", null, limit, pos);
    }

    // ── Validation des URL stockées ───────────────────────────────────────────

    /**
     * Valide une URL de GIF envoyée par un client avant de la mettre en base :
     * HTTPS obligatoire, hôte du fournisseur obligatoire ({@code static.klipy.com}).
     * On stocke des URL que d'autres lecteurs chargeront — accepter n'importe quel
     * hôte ferait de chaque commentaire un vecteur de traçage (ou pire) vers un
     * serveur arbitraire.
     *
     * @return l'URL inchangée, ou {@code null} si {@code null} en entrée.
     * @throws InvalidCommentException si l'URL ne vient pas du fournisseur.
     */
    public static String requireGifUrl(String url) {
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
        boolean allowedHost = host != null
                && (host.equals("klipy.com") || host.endsWith(".klipy.com"));
        if (!"https".equals(uri.getScheme()) || !allowedHost) {
            throw new InvalidCommentException("Seuls les GIF du sélecteur sont acceptés");
        }
        return url.strip();
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    private GifPageResponse fetch(String endpoint, String query, int limit, String pos) {
        if (!isAvailable()) {
            throw new GifUnavailableException(
                    "Les GIF ne sont pas configurés sur ce serveur (clé KLIPY absente)");
        }

        int clamped = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        int page = pageOf(pos);
        JsonNode root;
        try {
            root = restClient.get()
                    .uri(builder -> buildUri(builder, endpoint, query, clamped, page))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            // Fournisseur injoignable, quota épuisé, clé révoquée… L'utilisateur n'y
            // peut rien : un 503 franc avec un message lisible, pas une stacktrace.
            throw new GifUnavailableException("La recherche de GIF est indisponible pour le moment");
        }
        if (root == null || !root.path("result").asBoolean(false)) {
            throw new GifUnavailableException("La recherche de GIF est indisponible pour le moment");
        }

        JsonNode data = root.path("data");
        List<GifResponse> results = new ArrayList<>();
        for (JsonNode item : data.path("data")) {
            // Les résultats publicitaires de KLIPY (type « ad ») n'ont pas la même
            // forme et n'ont rien à faire dans un fil de lecture : on ne garde que
            // les vrais GIF.
            if (!"gif".equals(item.path("type").asString("gif"))) {
                continue;
            }
            JsonNode small = item.path("file").path("sm");
            JsonNode animated = small.path("gif");
            String url = animated.path("url").asString("");
            if (url.isEmpty()) {
                continue; // pas de version 220 px : résultat inutilisable, on saute
            }
            results.add(new GifResponse(
                    item.path("slug").asString(item.path("id").asString("")),
                    url,
                    // Pas de JPEG figé fourni → on retombe sur l'animé : l'app le
                    // décodera sans l'animer (première image), moins net mais jamais
                    // une case vide.
                    small.path("jpg").path("url").asString(url),
                    animated.path("width").asInt(0),
                    animated.path("height").asInt(0)));
        }
        // KLIPY pagine par numéro de page, là où Tenor rendait un curseur opaque.
        // On garde la forme « curseur » côté client (une chaîne à renvoyer telle
        // quelle) : l'app n'a jamais eu à savoir ce qu'il y a dedans, et n'a donc
        // rien à changer.
        String next = data.path("has_next").asBoolean(false) ? String.valueOf(page + 1) : "";
        return new GifPageResponse(results, next);
    }

    /** Le curseur rendu à la page précédente, ou la première page à défaut. */
    private static int pageOf(String pos) {
        if (pos == null || pos.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(pos.strip()));
        } catch (NumberFormatException ex) {
            // Curseur trafiqué ou hérité d'une autre version : on recommence au
            // début plutôt que de renvoyer une erreur pour si peu.
            return 1;
        }
    }

    private URI buildUri(UriBuilder builder, String endpoint, String query, int limit, int page) {
        // La clé d'API voyage dans le CHEMIN chez KLIPY, pas en paramètre.
        builder.path("/{key}/gifs/" + endpoint)
                .queryParam("page", page)
                .queryParam("per_page", limit)
                .queryParam("format_filter", FORMATS)
                .queryParam("content_filter", CONTENT_FILTER)
                .queryParam("locale", "fr_FR");
        if (query != null && !query.isBlank()) {
            builder.queryParam("q", query.strip());
        }
        return builder.build(apiKey);
    }
}
