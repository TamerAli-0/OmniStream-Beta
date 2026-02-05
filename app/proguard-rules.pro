# OmniStream ProGuard Rules

# Keep source interfaces for reflection-based source loading
-keep class com.omnistream.source.** { *; }
-keep interface com.omnistream.source.model.** { *; }

# Keep domain models
-keep class com.omnistream.domain.model.** { *; }

# Keep all DTOs (Data Transfer Objects) - critical for API communication
-keep class com.omnistream.data.remote.dto.** { *; }
-keep class com.omnistream.data.**.dto.** { *; }

# AniList API models
-keep class com.omnistream.data.anilist.AniListUser { *; }
-keep class com.omnistream.data.anilist.AniListMedia { *; }
-keep class com.omnistream.data.anilist.AniListStatistics { *; }
-keep class com.omnistream.data.anilist.AniListStatus { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-keeppackagenames org.jsoup.nodes
-dontwarn org.jspecify.annotations.**

# Retrofit - CRITICAL for GitHub API and any Retrofit usage
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Retrofit + Kotlinx Serialization Converter
-keepclassmembers,allowobfuscation class * {
    @com.jakewharton.retrofit2.converter.kotlinx.serialization.* <fields>;
}
-keep,includedescriptorclasses class com.jakewharton.retrofit2.converter.kotlinx.serialization.**$$serializer { *; }
-keepclassmembers class com.jakewharton.retrofit2.converter.kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class com.jakewharton.retrofit2.converter.kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Kotlinx Serialization - Enhanced rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
    *** descriptor;
}
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class org.jetbrains.annotations.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Coil
-keep class coil.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# DataStore
-keep class androidx.datastore.*.** { *; }

# DownloadManager for in-app updates
-keep class android.app.DownloadManager { *; }
-keep class android.app.DownloadManager$* { *; }

# FileProvider for APK installation
-keep class androidx.core.content.FileProvider { *; }
