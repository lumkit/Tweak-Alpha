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

############################################
# R8 极限压缩优化（现代 Android 推荐）
############################################

############################################
# 混淆字典（核心压缩点）
############################################
-obfuscationdictionary prod-dict.txt
-classobfuscationdictionary prod-dict.txt
-packageobfuscationdictionary prod-dict.txt

# coil3
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keepclassmembers class * implements android.content.ServiceConnection {
    public void onServiceConnected(android.content.ComponentName, android.os.IBinder);
    public void onServiceDisconnected(android.content.ComponentName);
}
# Shizuku UserService 需要保留无参构造函数和 AIDL Stub
-keep class io.github.lumkit.tweak.sharednative.ShizukuFileService { *; }
-keep class io.github.lumkit.tweak.sharednative.IRootFileService { *; }
-keep class io.github.lumkit.tweak.sharednative.IRootFileService$Stub { *; }
-keep class io.github.lumkit.tweak.sharednative.IRootFileService$Stub$Proxy { *; }

