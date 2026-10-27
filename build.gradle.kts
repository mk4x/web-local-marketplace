plugins {
    kotlin("jvm") version "2.4.0"
    id("io.ktor.plugin") version "3.6.0" // adds the run and buildFatJar tasks, and sets Ktor library versions
}

application {
    mainClass = "com.example.ApplicationKt"
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
}
