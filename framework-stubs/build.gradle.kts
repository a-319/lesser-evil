import java.util.Properties

// Declarations of hidden framework interfaces, kept in the framework's own packages so that
// app code can name them at compile time. The app consumes this module with compileOnly, so
// nothing here is packaged into the APK: at runtime the boot class loader's real classes load.
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// The stubs extend framework types (Binder, IInterface), so they compile against android.jar.
// There is no Android plugin here to hand it over, so find the SDK the way one would.
val sdkDir: Provider<String> =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty("sdk.dir").orEmpty() }
        .orElse("")
        .filter { it.isNotEmpty() }
        .orElse(providers.environmentVariable("ANDROID_HOME"))
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orElse("")

val androidJar: Provider<File> = sdkDir.map { dir ->
    if (dir.isEmpty()) {
        error("No Android SDK: set sdk.dir in local.properties, or ANDROID_HOME in the environment")
    }
    val platforms = File(dir, "platforms")
    val jars = (platforms.listFiles().orEmpty())
        .map { File(it, "android.jar") }
        .filter { it.isFile }
    // Any platform declares these types; the newest is the one the app compiles against.
    jars.maxByOrNull { it.parentFile.name.removePrefix("android-").toIntOrNull() ?: -1 }
        ?: error("No android.jar found under $platforms")
}

dependencies {
    compileOnly(files(androidJar))
}
