// Same build tools as Metrolist (Android Gradle Plugin 9, Kotlin 2.4), so both build on the same setup.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.4.1")
        classpath(kotlin("gradle-plugin", "2.4.20"))
    }
}
