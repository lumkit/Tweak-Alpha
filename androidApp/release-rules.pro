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

# TweakServer（app_process 按类名反射入口，禁止混淆/裁剪）
-keep class io.github.lumkit.tweak.server.TweakServerMain { *; }
-keep class io.github.lumkit.tweak.server.** { *; }
-keep class io.github.lumkit.tweak.server.ITweakServer { *; }
-keep class io.github.lumkit.tweak.server.ITweakServer$Stub { *; }
-keep class io.github.lumkit.tweak.server.ITweakServer$Stub$Proxy { *; }
-keep class io.github.lumkit.tweak.provider.TweakBinderProvider { *; }

# framework.jar stubs / Hidden API 引用（运行时由系统提供）
-dontwarn android.app.IAlarmManager
-dontwarn android.app.PropertyInvalidatedCache**
-dontwarn android.app.timedetector.**
-dontwarn android.util.MemoryIntArray
-dontwarn android.util.Singleton
-dontwarn com.android.internal.**
-dontwarn dalvik.system.BlockGuard$VmPolicy

