# Add project specific ProGuard rules here.
# Preserve WebView JavascriptInterface methods for release AAB/APK builds
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
