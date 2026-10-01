plugins {
    `java-library`
}

// Declarations of framework interfaces that are not part of the SDK, so that app code can name
// them at compile time. They have to carry the framework's own package names to be the same types
// the platform will hand back at runtime, which is exactly why they must not be packaged: an APK
// carrying its own android.app.admin.IDevicePolicyManager ships a shadow of a class it does not
// own, and a shrinker is then free to rename or merge its members. :app takes this as compileOnly,
// so nothing here reaches the APK and there is nothing for a shrinker to touch.

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Only ComponentName, Binder, IBinder and IInterface are needed to declare these, and all four
    // have been in Android since API 1, so the published platform jar is enough and this module
    // needs nothing from a locally installed SDK. compileOnly here as well, so it is not passed on
    // to whatever depends on this module.
    compileOnly("com.google.android:android:4.1.1.4")
}
