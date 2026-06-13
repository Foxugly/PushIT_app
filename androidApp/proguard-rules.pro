# R8 / ProGuard rules for the PushIT release build.
#
# Most libraries (kotlinx.serialization, Ktor, Firebase, OkHttp) ship their own
# consumer rules, which R8 applies automatically. The rules below are the
# explicit, project-scoped safety net — chiefly so kotlinx.serialization keeps
# the generated serializers for our @Serializable API DTOs.

# --- kotlinx.serialization (JetBrains-recommended, scoped to our package) ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep the generated $$serializer classes (referenced reflectively by name).
-keepclassmembers class com.foxugly.pushit_app.**$$serializer { *; }

# Keep the companion that exposes serializer() for @Serializable types.
-keepclassmembers class com.foxugly.pushit_app.** {
    *** Companion;
}
-keepclasseswithmembers class com.foxugly.pushit_app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Belt-and-suspenders: keep the API DTOs intact (they cross the JSON boundary).
-keep @kotlinx.serialization.Serializable class com.foxugly.pushit_app.data.api.** { *; }

# --- Ktor / SLF4J (no-op logger on Android) ---
-dontwarn org.slf4j.**
-dontwarn io.ktor.**

# --- Firebase Cloud Messaging service (instantiated by the framework) ---
-keep class com.foxugly.pushit_app.PushItFirebaseService { *; }
