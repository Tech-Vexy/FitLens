# FitLens Proguard / R8 Optimization & Obfuscation Rules

# 0. Application & Entry-Point Classes
#    R8 must never rename or remove these — Android instantiates them by the
#    exact class name declared in AndroidManifest.xml via reflection.
-keep public class com.fitlens.app.FitLensApp { *; }
-keep public class com.fitlens.app.MainActivity { *; }

# 1. Google ADK Kotlin & Tool Annotations
-keep class com.google.adk.** { *; }
-keep interface com.google.adk.** { *; }
-keep class com.fitlens.app.ai.adk.** { *; }
-keepclassmembers class com.fitlens.app.ai.FitnessTools {
    @com.fitlens.app.ai.adk.Tool *;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 2. Local SQLite Room Database & Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class com.fitlens.app.data.model.** { *; }
-keep @androidx.room.Dao interface com.fitlens.app.data.dao.** { *; }

# 3. MediaPipe Pose Estimation
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# 4. Presage SmartSpectra SDK
-keep class com.presagetech.smartspectra.** { *; }
-dontwarn com.presagetech.smartspectra.**

# 5. Firebase App Check & Play Integrity
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.appcheck.**

# 6. Jetpack DataStore Preferences
-keep class androidx.datastore.preferences.** { *; }
