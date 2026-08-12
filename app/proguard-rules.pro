# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
# You can edit the included proguard-android-optimize.txt to only include
# what you find necessary for your project's usage.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefile SourceFile

# ---------------------------------------------------------------------------
# ML Kit GenAI structured output — the highest-risk rule in this file.
#
# `OrganizedResponse` (ai/OnDeviceSummarizer.kt) is annotated @Generable/@Serializable.
# `ksp("com.google.mlkit:genai-schema-compiler")` generates `OrganizedResponse_GeneratedProvider`
# at compile time, which the ML Kit runtime looks up by name/reflection to build the JSON schema
# for the typed generateContent() call (structuredOrganize()).
#
# If R8 strips or renames either class, `isStructuredOutputFeatureAvailable()` /
# structuredOrganize() fails at runtime — but it fails inside a try/catch that falls back to
# promptJsonOrganize() (the prose-JSON path). That fallback still produces a plausible
# OrganizedContent, so THE APP KEEPS WORKING AND THE BUILD KEEPS SUCCEEDING. This is a silent
# regression: schema-enforced category/tag/bullet counts silently degrade to "asked nicely in the
# prompt". There is no crash and no visible signal, which is exactly why it needs an explicit
# keep rather than relying on R8 to infer it from usage.
-keep class com.example.stash.ai.OrganizedResponse { *; }
-keep class com.example.stash.ai.OrganizedResponse$* { *; }
-keep class com.example.stash.ai.OrganizedResponse_GeneratedProvider { *; }
-keep class com.example.stash.ai.OrganizedResponse_GeneratedProvider$* { *; }

# The wider ML Kit GenAI surface (Generation client, structured-output/schema plumbing,
# annotations). This is a Beta artifact (structured output is Alpha within it) that leans on
# reflection for schema generation, so keep it wholesale rather than chasing individual members.
-keep class com.google.mlkit.genai.** { *; }
-keep interface com.google.mlkit.genai.** { *; }
-keep @interface com.google.mlkit.genai.**
-keepclassmembers class * {
    @com.google.mlkit.genai.schema.annotations.Generable *;
    @com.google.mlkit.genai.schema.annotations.Guide *;
}

# ---------------------------------------------------------------------------
# kotlinx.serialization
#
# OrganizedResponse is also @Serializable (used by the promptJsonOrganize() fallback above, via
# kotlinx.serialization.json.Json), and the Nav3 route keys (FeedRoute, ChatRoute in
# StashAdaptiveLayout.kt) are @Serializable too. The serializer classes are generated as
# synthetic companions (`Companion.serializer()`) that reflection-based lookup depends on;
# without a keep, R8 can strip them since nothing calls them by a name it can see statically.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.stash.**$$serializer { *; }
-keepclassmembers class com.example.stash.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.stash.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Room
#
# This project uses the androidx.room3 artifact (Room's KMP-ready line, not the older
# androidx.room), with KSP generating DAO/database implementations at compile time
# (e.g. StashDatabase_Impl) that are referenced by generated code rather than by name from our
# source, and the bundled SQLite driver (androidx.sqlite.bundled) uses JNI bindings that must
# keep their class/member names. AGP's default Android rules already handle most Room cases, but
# the entity/DAO/database annotations are kept explicitly here so shrinking a constructor or
# field of `StashEntity`/`StashSearchEntity`/`StashDao` can't silently corrupt a row mapping the
# way the ML Kit schema bug could.
-keep @androidx.room3.Entity class * { *; }
-keep @androidx.room3.Database class * { *; }
-keep @androidx.room3.Dao class * { *; }
-keep class * extends androidx.room3.RoomDatabase { *; }
-keep class com.example.stash.data.local.** { *; }
-dontwarn androidx.room3.paging.**

# ---------------------------------------------------------------------------
# Standard AndroidX / Kotlin coroutines noise that R8 otherwise warns about but that is safe to
# ignore for a minSdk 34 app (these are multiplatform/desktop code paths never exercised on
# Android).
-dontwarn kotlinx.coroutines.debug.**
-dontwarn org.jetbrains.annotations.**
