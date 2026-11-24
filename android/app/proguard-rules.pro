# UniFFI / JNA (Play deobfuscation + R8)
-keep class uniffi.mycena.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

-keepclassmembers class io.github.mycena_project.app.ui.MiniappJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Tink (EncryptedSharedPreferences) — annotation classes not on the runtime classpath
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
