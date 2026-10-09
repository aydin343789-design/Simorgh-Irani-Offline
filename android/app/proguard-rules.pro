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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve the annotation and the narrow JavaScript bridge under R8.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keep class ir.simorgh.irani.SimorghTranslatorBridge { *; }
-keep class ir.simorgh.irani.SimorghSpeechBridge { *; }
-keep class ir.simorgh.irani.MainActivity { *; }
-keep class com.k2fsa.sherpa.onnx.OfflineTts { *; }
-keep class com.k2fsa.sherpa.onnx.OfflineTtsConfig { *; }
-keep class com.k2fsa.sherpa.onnx.OfflineTtsModelConfig { *; }
-keep class com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig { *; }
-keep class com.k2fsa.sherpa.onnx.GeneratedAudio { *; }
-keep class com.k2fsa.sherpa.onnx.LibraryLoader { *; }
-keep class com.k2fsa.sherpa.onnx.LibraryUtils { *; }
