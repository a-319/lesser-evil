# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# -dontobfuscate
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# -renamesourcefileattribute SourceFile

-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.LoadedApk

-keep class lesser.evil.MyViewModel { *; }

# This app carries declarations of hidden framework interfaces so that it can compile against
# them. At runtime the framework's own are what load, so a member of ours that was renamed, or a
# class of ours that was merged into another, leaves the app asking the real class for something
# it has never heard of - which surfaces as a NoSuchFieldError on a field called "a". Nothing in a
# namespace this app does not own may be touched.
-keep class android.app.admin.** { *; }
-keep class android.content.pm.IPackageInstaller { *; }
-keep class android.content.pm.IPackageInstaller$* { *; }
