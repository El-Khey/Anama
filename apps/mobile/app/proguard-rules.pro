# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Une trace d'un build minifié est illisible sans ça : on garde les numéros de ligne,
# et on masque le nom du fichier d'origine (R8 le remplace par « SourceFile »).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Retrofit, OkHttp et Coil embarquent leurs propres règles. kotlinx.serialization aussi,
# mais elles reposent sur le `Companion` sérialiseur, que R8 peut écarter faute de voir
# quelqu'un l'appeler : nos DTO ne sont instanciés que par réflexion, depuis Retrofit.
# Un manque ici ne se voit PAS à la compilation — il se manifeste à l'exécution, en
# release uniquement, par un « Serializer for class … not found ».
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.novelrealm.mobile.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.novelrealm.mobile.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.novelrealm.mobile.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit ────────────────────────────────────────────────────────────────
# Le type de retour de chaque méthode d'API est lu par réflexion sur la signature
# générique : sans elle, Retrofit ne sait plus quoi désérialiser.
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.novelrealm.mobile.data.remote.api.*

# ── Annotations absentes à l'exécution ──────────────────────────────────────
# Tink — le moteur de chiffrement derrière EncryptedSharedPreferences, donc
# derrière notre TokenStorage — référence des annotations d'ErrorProne qui
# n'existent qu'à la COMPILATION et ne sont pas embarquées dans l'APK. R8 le
# signale, et refuse de terminer.
#
# `-dontwarn` est la bonne réponse ici, pas `-keep` : on ne demande pas de
# conserver ces classes (elles n'existent pas), on dit que leur absence est
# attendue. Rien n'y fait appel à l'exécution.
#
# Liste produite par AGP lui-même, à relire si d'autres apparaissent un jour :
#   app/build/outputs/mapping/release/missing_rules.txt
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi