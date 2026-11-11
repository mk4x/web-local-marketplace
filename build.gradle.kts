buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:13.11.1")
    }
}

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.ktor.plugin") version "3.6.0"
    id("org.jooq.jooq-codegen-gradle") version "3.21.9"
    id("org.flywaydb.flyway") version "13.11.1"
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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.4")
    implementation("com.graphql-java:graphql-java:26.1")

    // Database and migrations
    implementation("org.flywaydb:flyway-core:13.11.1")
    implementation("org.flywaydb:flyway-database-postgresql:13.11.1")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.jooq:jooq:3.21.9")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    runtimeOnly("org.postgresql:postgresql")

    jooqCodegen("org.postgresql:postgresql:42.7.14")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

val generatedJooqDir = layout.projectDirectory.dir("src/main/generated")

val dbUrl =
    providers
        .environmentVariable("DB_URL")
        .orElse("jdbc:postgresql://localhost:5432/app")
val dbUser =
    providers
        .environmentVariable("DB_USER")
        .orElse("app")
val dbPassword =
    providers
        .environmentVariable("DB_PASSWORD")
        .orElse("app")

sourceSets {
    main {
        java.srcDir(generatedJooqDir)
    }
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = dbUrl.get()
            user = dbUser.get()
            password = dbPassword.get()
        }

        generator {
            name = "org.jooq.codegen.JavaGenerator"

            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                isIncludeDomains = false
                isIncludeRoutines = false
            }

            generate {
                isPojos = true
                isDaos = false
                isRecords = true
                isImmutablePojos = true
            }

            target {
                packageName = "com.g7.database.jooq"
                directory = generatedJooqDir.asFile.absolutePath
            }
        }
    }
}

flyway {
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/app"
    user = System.getenv("DB_USER") ?: "app"
    password = System.getenv("DB_PASSWORD") ?: "app"
}

tasks.named("jooqCodegen") {
    dependsOn("flywayMigrate")
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}

tasks.test {
    useJUnitPlatform()
}
