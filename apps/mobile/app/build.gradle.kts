import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Adresse de l'API, réglable SANS modifier ce fichier (donc sans rien commiter).
//
//  Priorité : local.properties  >  -Pnovelrealm.baseUrl=…  >  valeur par défaut.
//  `local.properties` n'est jamais versionné : chacun y met l'adresse qui
//  correspond à sa façon de tester.
//
//  • Émulateur (défaut)      → http://10.0.2.2:8080/   (10.0.2.2 = « localhost » du PC)
//  • Téléphone en USB        → http://localhost:8080/  + `adb reverse tcp:8080 tcp:8080`
//  • Téléphone en Wi-Fi      → http://<IP-LAN-du-PC>:8080/  (pare-feu à ouvrir)
// ─────────────────────────────────────────────────────────────────────────────
val apiBaseUrl: String = run {
    val fromLocalProperties = rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
        ?.getProperty("novelrealm.baseUrl")

    val raw = fromLocalProperties
        ?: (project.findProperty("novelrealm.baseUrl") as String?)
        ?: "http://10.0.2.2:8080/"

    // Retrofit EXIGE une URL de base terminée par « / » (sinon il lève une exception
    // au démarrage) : on la remet si elle manque, plutôt que de planter à l'exécution.
    raw.trim().let { if (it.endsWith("/")) it else "$it/" }
}

android {
    namespace = "com.novelrealm.mobile"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.novelrealm.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Voir le calcul de `apiBaseUrl` en haut du fichier : réglable via
        // local.properties (novelrealm.baseUrl), sans rien modifier ici.
        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ── #32 : réseau, sérialisation, coroutines, ViewModel Compose ──
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // ── #33 : stockage sécurisé du JWT (EncryptedSharedPreferences) ──
    implementation(libs.androidx.security.crypto)

    // ── #34 : navigation à onglets + jeu d'icônes Material complet ──
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // ── #35 : chargement d'images (couvertures) ──
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
