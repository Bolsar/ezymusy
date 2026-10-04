# NewPipeExtractor: rules taken from the NewPipe app.
# Rhino runs YouTube's player JS (signature/throttling deobfuscation) via reflection.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
# Rhino's invokedynamic linker; JDK-only, never used on Android.
-dontwarn jdk.dynalink.**
# "x minutes ago" parsers are loaded by class name per locale.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Playlist and mix paging builds protobuf-lite messages, which find their fields by name via reflection.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
