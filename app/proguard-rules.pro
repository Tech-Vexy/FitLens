# FitLens Proguard / R8 Optimization & Obfuscation Rules

# 0. Application & Entry-Point Classes
#    R8 must never rename or remove these — Android instantiates them by the
#    exact class name declared in AndroidManifest.xml via reflection, and CI
#    explicitly verifies their presence in classes*.dex.
-keep public class com.fitlens.app.FitLensApp { *; }
-keep public class com.fitlens.app.MainActivity { *; }

# 1. Google ADK Kotlin, LocalEdgeEngine Reflection & Tool Annotations
-keep class com.google.adk.** { *; }
-keep interface com.google.adk.** { *; }
-keep class com.fitlens.app.ai.** { *; }
-keepclassmembers class com.fitlens.app.ai.FitnessTools {
    @com.fitlens.app.ai.adk.Tool *;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, MethodParameters

# 2. Local SQLite Room Database & Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class com.fitlens.app.data.model.** { *; }
-keep @androidx.room.Dao interface com.fitlens.app.data.dao.** { *; }

# 3. MediaPipe Pose Estimation & JNI
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# 4. Presage SmartSpectra SDK & Protobuf Messages (MetricsProto / InsightsProto)
-keep class com.presagetech.smartspectra.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.presagetech.smartspectra.**
-dontwarn com.google.protobuf.**

# 5. Firebase AI Logic, App Check & Play Integrity
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.ai.**
-dontwarn com.google.firebase.appcheck.**

# 6. Jetpack DataStore Preferences
-keep class androidx.datastore.preferences.** { *; }
