buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:13.11.1")
    }
}

val generatedJooqDir = file("src/generated/jooq")

sourceSets {
    main {
        java.srcDir(generatedJooqDir)
    }
}

plugins {
    kotlin("jvm") version "2.4.0"
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

    // Database and migrations
    implementation("org.flywaydb:flyway-core:13.11.1")
    implementation("org.flywaydb:flyway-database-postgresql:13.11.1")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.jooq:jooq:3.21.9")
    implementation("org.postgresql:postgresql:42.7.14")

    jooqCodegen("org.postgresql:postgresql:42.7.14")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test-junit5"))
}


sourceSets {
    main {
        java.srcDir(generatedJooqDir)
    }
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = providers.environmentVariable("DB_URL").orNull
                ?: "jdbc:postgresql://localhost:5432/marketplace"
            user = providers.environmentVariable("DB_USER").orNull
                ?: "postgres"
            password = providers.environmentVariable("DB_PASSWORD").orNull
                ?: "postgres"
        }

        generator {
            name = "org.jooq.codegen.JavaGenerator"

            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
            }

            generate {
                isPojos = true
                isDaos = false
                isRecords = true
                isImmutablePojos = true
            }

            target {
                packageName = "com.g7.database.jooq"
                directory = generatedJooqDir.absolutePath
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
