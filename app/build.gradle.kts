plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// CI stamps every build with its run number as the versionCode, so each APK
// installs over the previous one as an update. The versionName shown to people
// is worked out by the workflow (1.0.X on main, 1.0.X-RUN on other branches).
// Local builds stay at 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val buildVersionName = System.getenv("VERSION_NAME") ?: "0-local"

// The repo is public, so the signing key isn't in it. CI decodes the
// SIGNING_KEYSTORE_BASE64 secret to this path and passes SIGNING_PASSWORD; a
// build without them (local, or a fork) falls back to the debug key, which
// installs fine but won't update an APK signed with the real one.
val sideloadKeystore = rootProject.file("signing/sideload.keystore")
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")

// Robolectric fetches its Android runtime (a 240 MB jar) from Maven Central by
// itself, into ~/.m2, when the tests start: on every CI run, since nothing
// caches ~/.m2, and often refused with 429 Too Many Requests. Resolving it as
// a Gradle dependency puts it in Gradle's cache (which CI keeps) and through
// the same repositories as everything else; the tests then run offline.
val robolectricRuntime: Configuration = configurations.create("robolectricRuntime")
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val robolectricJars = tasks.register<Sync>("robolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarsDir)
}

android {
    namespace = "fyi.mergers.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "fyi.mergers.app"
        // Only the current Android: 17 (API 37).
        minSdk = 37
        targetSdk = 37
        versionCode = buildNumber
        versionName = buildVersionName
    }

    signingConfigs {
        if (sideloadKeystore.exists() && signingPassword != null) {
            create("sideload") {
                storeFile = sideloadKeystore
                storePassword = signingPassword
                keyAlias = "mergers"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // Two AndroidX native libraries AGP can't strip without an NDK; they
        // ship as they come either way, so don't try (or warn about it).
        jniLibs.keepDebugSymbols += setOf(
            "**/libandroidx.graphics.path.so",
            "**/libdatastore_shared_counter.so",
        )
    }

    testOptions {
        // Robolectric needs the merged resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's Android sandbox reaches into JDK internals.
            it.dependsOn(robolectricJars)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.path)
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.browser)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    robolectricRuntime(libs.robolectric.android.all)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Newer than the Compose test library pulls in: 3.5 can't idle on Android 17.
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
