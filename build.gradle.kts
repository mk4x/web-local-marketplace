plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.ktor.plugin") version "3.6.0"
}

application {
    mainClass = "com.g7.ApplicationKt"
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:1.5.37")

    implementation("org.flywaydb:flyway-core:11.14.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.14.0")
    implementation("org.postgresql:postgresql:42.7.8")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test-junit5"))
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}

tasks.test {
    useJUnitPlatform()
}
