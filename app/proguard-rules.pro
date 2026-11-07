# Rules bổ sung cho bản Release AAB / APK

# 1. Bảo vệ JavaScript Bridge & @JavascriptInterface
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.vttd.sieutrangnhi.AndroidBridge { *; }
-keepclassmembers class com.vttd.sieutrangnhi.AndroidBridge {
    public *;
}

# 2. Bảo vệ Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }

# 3. Bảo vệ Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.mediation.** { *; }

# 4. Bảo vệ OkHttp, Moshi & Network Annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# 5. Giữ lại thông tin Stack trace để dễ Debug lỗi Production
-keepattributes SourceFile,LineNumberTable
