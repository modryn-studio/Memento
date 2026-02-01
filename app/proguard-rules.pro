# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep ONNX Runtime classes
-keep class com.microsoft.onnxruntime.** { *; }
-keepclassmembers class com.microsoft.onnxruntime.** { *; }

# Keep Room entities
-keep class studio.modryn.memento.data.database.entity.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin serialization (if added later)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
