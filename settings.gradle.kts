plugins {
    // Lets Gradle download the Java 21 that build.gradle.kts asks for
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ktor-sample"
