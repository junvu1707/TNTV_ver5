# ProGuard / R8 rules for Siêu Trạng Nhí Tiếng Việt

# Preserve JavascriptInterface annotations and methods for WebView Bridge
-keepattributes *Annotation*,JavascriptInterface

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class com.vttd.sieutrangnhi.AndroidBridge {
    public *;
}

# Preserve QuestionModel fields and constructor for JSON parsing
-keepclassmembers class com.vttd.sieutrangnhi.QuestionModel {
    public *;
}

# Preserve line number information for crash debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile



