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
// Vide si le fichier n'existe pas — chaque lecture rend alors simplement `null`.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val apiBaseUrl: String = run {
    val raw = localProperties.getProperty("novelrealm.baseUrl")
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

        // versionCode DOIT augmenter à chaque version distribuée : c'est lui, et lui
        // seul, qui dit à Android « ceci est une mise à jour ». Un appareil refuse
        // d'installer par-dessus un versionCode égal ou supérieur — et sans mise à jour
        // possible, la seule issue devient la désinstallation, qui, elle, efface les
        // données locales. versionName ne sert qu'à l'affichage.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Voir le calcul de `apiBaseUrl` en haut du fichier : réglable via
        // local.properties (novelrealm.baseUrl), sans rien modifier ici.
        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Clé de signature de distribution — déclarée seulement si elle est fournie.
    //
    //  Android n'accepte une mise à jour QUE si elle porte la même signature que la
    //  version installée. Publier avec une clé, puis en changer, laisse chaque
    //  utilisateur sur une impasse : plus aucune mise à jour ne s'installe, il faut
    //  désinstaller — et la désinstallation, elle, efface les données locales.
    //  Cette clé se garde donc précieusement : la perdre, c'est perdre la capacité de
    //  mettre l'app à jour, définitivement.
    //
    //  Comme l'adresse de l'API, elle se règle dans local.properties (jamais versionné,
    //  et il ne DOIT pas l'être — il contiendrait les mots de passe de la clé) :
    //
    //      novelrealm.storeFile=cles/novelrealm.jks      (chemin relatif à la racine)
    //      novelrealm.storePassword=…
    //      novelrealm.keyAlias=novelrealm
    //      novelrealm.keyPassword=…
    // ─────────────────────────────────────────────────────────────────────────
    val releaseKeystore = localProperties.getProperty("novelrealm.storeFile")
        ?.let { rootProject.file(it) }
        ?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("novelrealm.storePassword")
                keyAlias = localProperties.getProperty("novelrealm.keyAlias")
                keyPassword = localProperties.getProperty("novelrealm.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 : retire le code mort et raccourcit le graphe de classes. C'est ce qui
            // sépare vraiment un build de développement d'un build livré —
            // `material-icons-extended` à lui seul embarque des milliers d'icônes dont
            // l'app en utilise une trentaine, et chaque classe qui survit est une classe
            // à charger au premier affichage d'un écran.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Sans vraie clé, on retombe sur celle de debug : c'est ce qui permet
            // d'installer et de MESURER un build release depuis un poste de dev — sans
            // aucune signature, `assembleRelease` produit un APK non signé, donc non
            // installable. Mais un APK signé debug ne doit jamais être distribué : la
            // clé de debug est publique, partagée par tous les postes, et elle expire.
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "NovelRealm : build release signé avec la clé de DEBUG " +
                        "(aucun novelrealm.storeFile dans local.properties). " +
                        "Bon pour tester sur son propre téléphone, jamais pour distribuer."
                )
                signingConfigs.getByName("debug")
            }
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
