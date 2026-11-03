plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.ktor.plugin") version "3.6.0"
    id("de.quati.pgen") version "0.52.1"
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

    // pgen JDBC runtime
    implementation("de.quati.pgen:jdbc:0.51.0")

    // JetBrains Exposed
    val exposedVersion = "1.0.0-rc-4"

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation(
        "org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion",
    )
    implementation("org.jetbrains.exposed:exposed-crypt:$exposedVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("de.quati:kotlin-util:2.0.0")
    implementation("org.locationtech.jts:jts-core:1.20.0")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test-junit5"))
}

pgen {
    addDb("main") {
        connection {
            url(
                providers
                    .environmentVariable("DB_URL")
                    .orElse("jdbc:postgresql://localhost:5432/app")
                    .get(),
            )
            user(
                providers
                    .environmentVariable("DB_USER")
                    .orElse("app")
                    .get(),
            )
            password(
                providers
                    .environmentVariable("DB_PASSWORD")
                    .orElse("app")
                    .get(),
            )
        }
        flyway {
            migrationDirectory("$projectDir/src/main/resources/db/migration")
        }
        tableFilter {
            addSchemas("public")
        }
        typeMappings {
            add("pg_catalog.name", clazz = "kotlin.String")
            add("pg_catalog.inet", clazz = "java.net.InetAddress")
            add("pg_catalog.tsvector", clazz = "kotlin.String")

            add(
                "public.geography",
                clazz = "com.g7.database.types.Geography",
            )
            add(
                "pg_catalog.tstzrange",
                clazz = "com.g7.database.types.TstzRange",
            )
            add(
                "pg_catalog.tstzmultirange",
                clazz = "com.g7.database.types.TstzMultiRange",
            )
            add(
                "public.ltree",
                clazz = "com.g7.database.types.Ltree",
            )
        }
    }
    packageName("com.g7.generated")
    specFilePath(
        "$projectDir/src/main/resources/pgen-spec.json",
    )
    setConnectionTypeJdbc()
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
