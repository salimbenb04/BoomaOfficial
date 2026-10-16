# --- NewPipe Extractor (rules from its README) ---
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# NewPipe Extractor and its JSON parser look some classes up by name; keep them whole.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.grack.nanojson.** { *; }

# Desktop-Java classes referenced by Rhino / jsoup that Android doesn't ship (never used on Android).
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**

# The widget's button actions are created by class name.
-keep class com.booma.app.widget.** { *; }

# Google Cast reads its options class name from the manifest; the device picker dialogs are fragments.
-keep class com.booma.app.cast.** { *; }
-keep class com.booma.app.ui.BoomaCastChooser { *; }
-keep class com.booma.app.ui.BoomaCastController { *; }

# Don't stop the build over references to classes that only exist on desktop Java.
-ignorewarnings
