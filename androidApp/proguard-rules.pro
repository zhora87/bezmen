# ONNX Runtime: the JNI layer looks classes up by name and creates OrtException from native code.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# kotlinx.serialization: generated serializers of our models (locale packs, settings, comparison list).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class io.github.zhora87.bezmen.**$$serializer { *; }
-keepclassmembers class io.github.zhora87.bezmen.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.zhora87.bezmen.** {
    kotlinx.serialization.KSerializer serializer(...);
}
