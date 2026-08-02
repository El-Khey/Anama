package com.novelrealm.mobile.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.novelrealm.mobile.BuildConfig
import com.novelrealm.mobile.data.local.PreferencesStore
import com.novelrealm.mobile.data.local.SessionManager
import com.novelrealm.mobile.data.local.TokenStorage
import com.novelrealm.mobile.data.remote.AuthInterceptor
import com.novelrealm.mobile.data.remote.api.AuthApi
import com.novelrealm.mobile.data.remote.api.CategoryApi
import com.novelrealm.mobile.data.remote.api.ChapterApi
import com.novelrealm.mobile.data.remote.api.CommentApi
import com.novelrealm.mobile.data.remote.api.FavoriteApi
import com.novelrealm.mobile.data.remote.api.HistoryApi
import com.novelrealm.mobile.data.remote.api.LibraryApi
import com.novelrealm.mobile.data.remote.api.NovelApi
import com.novelrealm.mobile.data.remote.api.ProgressApi
import com.novelrealm.mobile.data.remote.api.ReviewApi
import com.novelrealm.mobile.data.remote.api.UserApi
import com.novelrealm.mobile.data.repository.AuthRepository
import com.novelrealm.mobile.data.repository.CategoryRepository
import com.novelrealm.mobile.data.repository.ChapterRepository
import com.novelrealm.mobile.data.repository.CommentRepository
import com.novelrealm.mobile.data.repository.FavoriteRepository
import com.novelrealm.mobile.data.repository.HistoryRepository
import com.novelrealm.mobile.data.repository.LibraryRepository
import com.novelrealm.mobile.data.repository.NovelRepository
import com.novelrealm.mobile.data.repository.ProgressRepository
import com.novelrealm.mobile.data.repository.ReviewRepository
import com.novelrealm.mobile.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

// DI manuelle (ServiceLocator) : construit le graphe réseau paresseusement.
//
// On n'utilise PAS Hilt : son plugin Gradle n'est pas compatible AGP 9
// (« Android BaseExtension not found »). Pour un projet de cette taille, une DI
// manuelle est simple et suffisante.
//
// init() doit être appelé une fois au démarrage (depuis NovelRealmApp) pour
// fournir le Context applicatif nécessaire au stockage sécurisé.
object ServiceLocator {

    private lateinit var appContext: Context

    // À appeler dans Application.onCreate().
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Scope applicatif : pour les écritures « fire-and-forget » (sauvegarde de progression)
    // qui doivent survivre à la destruction d'un ViewModel (sortie du lecteur).
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Stockage sécurisé du token (#33) + état de session (#35) ──
    val tokenStorage: TokenStorage by lazy { TokenStorage(appContext) }

    // Source de vérité unique « connecté / déconnecté », partagée par l'auth ET la
    // couche réseau (l'intercepteur la notifie sur un 401 → auto-déconnexion).
    val sessionManager: SessionManager by lazy {
        SessionManager(tokenStorage, onSessionEnded = { preferencesStore.onSessionEnded() })
    }

    // Préférences (thème + lecture) : appliquées localement tout de suite, poussées
    // vers le compte de façon débouncée et best-effort.
    val preferencesStore: PreferencesStore by lazy {
        PreferencesStore(
            context = appContext,
            scope = appScope,
            pushToServer = { preferences -> userRepository.updatePreferences(preferences) },
        )
    }

    // ── Réseau ──
    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true   // tolère les champs inconnus (ex. `token` aplati du login)
            isLenient = true
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Injecte le Bearer sur les appels authentifiés + déconnecte sur 401 (token expiré).
            .addInterceptor(AuthInterceptor(tokenStorage, onUnauthorized = { sessionManager.onUnauthorized() }))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // ── APIs ──
    val novelApi: NovelApi by lazy { retrofit.create(NovelApi::class.java) }
    private val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    private val chapterApi: ChapterApi by lazy { retrofit.create(ChapterApi::class.java) }
    private val commentApi: CommentApi by lazy { retrofit.create(CommentApi::class.java) }
    private val libraryApi: LibraryApi by lazy { retrofit.create(LibraryApi::class.java) }
    private val progressApi: ProgressApi by lazy { retrofit.create(ProgressApi::class.java) }
    private val historyApi: HistoryApi by lazy { retrofit.create(HistoryApi::class.java) }
    private val reviewApi: ReviewApi by lazy { retrofit.create(ReviewApi::class.java) }
    private val favoriteApi: FavoriteApi by lazy { retrofit.create(FavoriteApi::class.java) }
    private val categoryApi: CategoryApi by lazy { retrofit.create(CategoryApi::class.java) }
    private val userApi: UserApi by lazy { retrofit.create(UserApi::class.java) }

    // ── Repositories ──
    val authRepository: AuthRepository by lazy { AuthRepository(authApi, sessionManager) }
    val novelRepository: NovelRepository by lazy { NovelRepository(novelApi) }
    val chapterRepository: ChapterRepository by lazy { ChapterRepository(chapterApi) }
    val commentRepository: CommentRepository by lazy { CommentRepository(commentApi) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(libraryApi) }
    val progressRepository: ProgressRepository by lazy { ProgressRepository(progressApi) }
    val historyRepository: HistoryRepository by lazy { HistoryRepository(historyApi) }
    val reviewRepository: ReviewRepository by lazy { ReviewRepository(reviewApi) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(favoriteApi) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(categoryApi) }
    val userRepository: UserRepository by lazy { UserRepository(userApi) }
}
