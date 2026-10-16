plugins {
    id("com.android.application")
    // Kotlin is built into the Android Gradle Plugin 9; only the Compose compiler is added.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

android {
    namespace = "com.booma.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.booma.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.3.1"
    }

    buildTypes {
        // The smooth build: optimised and shrunk with R8. Signed with your debug key so it installs over
        // the debug build on your phone (fine for personal use; make a real key before sharing it).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // NewPipe Extractor uses newer Java APIs; desugaring makes them work below Android 13.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Newer Compose (as in Metrolist): faster list scrolling and less work per frame.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // FragmentActivity and the AppCompat theme for the Cast device picker
    implementation("androidx.appcompat:appcompat:1.8.0")

    val media3 = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-cast:$media3")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")

    // One shared HTTP client (HTTP/2, reused connections) for YouTube, the player and lyrics.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    // installs the libraries' startup profiles on sideloaded APKs, so screens are compiled ahead of time
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Stream extraction (signature / throttling decoding), the same library NewPipe and Metrolist use. GPL-3.0.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}
