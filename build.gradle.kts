import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.SchemaMappingType
import kotlin.apply

fun loadEnv(): Map<String, String> {
    // Real environment variables take precedence over the .env file. This lets
    // CI / sandboxed environments inject credentials via System.getenv without
    // requiring a .env file to exist on disk.
    val fileEnv =
        rootProject.file(".env").takeIf { it.exists() }?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.associate {
                val (k, v) = it.split("=", limit = 2)
                k.trim() to v.trim()
            }
            .orEmpty()

    val sysEnv =
        listOf(
            "SPRING_DATASOURCE_URL",
            "SPRING_DATASOURCE_USERNAME",
            "SPRING_DATASOURCE_PASSWORD",
        ).mapNotNull { key -> System.getenv(key)?.takeIf { it.isNotBlank() }?.let { key to it } }
            .toMap()

    return sysEnv + fileEnv
}

val env = loadEnv()

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    id("nu.studer.jooq") version "9.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "com.cocoa"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("com.nhaarman.mockito-kotlin:mockito-kotlin:5.2.1")

    // JOOQ
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq-kotlin:3.19.29")
    implementation("org.jooq:jooq:3.19.29")
    jooqGenerator("org.jooq:jooq-codegen:3.19.29")

    // Postgres
    implementation("org.postgresql:postgresql:42.7.3")
    jooqGenerator("org.postgresql:postgresql:42.7.3")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("org.springframework.security:spring-security-test")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Env
    implementation("me.paulschwarz:spring-dotenv:3.0.0")

    // Swagger
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Apache POI for Excel
    implementation("org.apache.poi:poi-ooxml:5.4.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

jooq {
    version.set("3.19.29")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        // Skip configuring the JOOQ generator entirely when no datasource
        // is available — gradle `tasks` and `help` commands then work
        // without a DB. The CI workflow sets SPRING_DATASOURCE_URL/USER/PASSWORD
        // via env vars, so on CI the generator runs normally.
        if (env["SPRING_DATASOURCE_URL"].isNullOrBlank() ||
            env["SPRING_DATASOURCE_USERNAME"].isNullOrBlank() ||
            env["SPRING_DATASOURCE_PASSWORD"].isNullOrBlank()
        ) {
            return@configurations
        }

        create("main") {
            // JOOQ schema generation is opt-in: run `./gradlew generateJooq`
            // (or let CI do it) to populate build/generated/jooq/ before
            // compiling. This keeps local builds (without a .env) from
            // failing on a missing SPRING_DATASOURCE_URL.
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = env["SPRING_DATASOURCE_URL"] ?: error("SPRING_DATASOURCE_URL not set in .env")
                    user = env["SPRING_DATASOURCE_USERNAME"] ?: error("SPRING_DATASOURCE_USERNAME not set in .env")
                    password = env["SPRING_DATASOURCE_PASSWORD"] ?: error("SPRING_DATASOURCE_PASSWORD not set in .env")
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    strategy.apply {
                        name = "org.jooq.codegen.DefaultGeneratorStrategy"
                    }
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        includes = ".*"
                        excludes = ""
                        withSchemata(
                            SchemaMappingType().withInputSchema("auth"),
                            SchemaMappingType().withInputSchema("ref"),
                            SchemaMappingType().withInputSchema("processing"),
                            SchemaMappingType().withInputSchema("agriculture"),
                            SchemaMappingType().withInputSchema("collection"),
                            SchemaMappingType().withInputSchema("storage"),
                            SchemaMappingType().withInputSchema("form"),
                            SchemaMappingType().withInputSchema("research"),
                        )
                        forcedTypes.addAll(
                            listOf(
                                ForcedType().apply {
                                    userType = "com.fasterxml.jackson.databind.JsonNode"
                                    binding = "com.cocoa.web.jooq.JsonbNodeBinding"
                                    includeTypes = "jsonb"
                                },
                                ForcedType().apply {
                                    userType = "com.fasterxml.jackson.databind.JsonNode"
                                    binding = "com.cocoa.web.jooq.JsonNodeBinding"
                                    includeTypes = "json"
                                },
                                ForcedType().apply {
                                    userType = "java.time.LocalDateTime"
                                    converter = "com.cocoa.web.jooq.LocalDateTimeConverter"
                                    includeTypes = "timestamp\\ without\\ time\\ zone"
                                },
                                ForcedType().apply {
                                    userType = "java.time.LocalDate"
                                    converter = "com.cocoa.web.jooq.LocalDateConverter"
                                    includeTypes = "date"
                                },
                                ForcedType().apply {
                                    userType = "java.time.LocalTime"
                                    converter = "com.cocoa.web.jooq.LocalTimeConverter"
                                    includeTypes = "time"
                                },
// 							ForcedType().apply {
// 								userType = "net.postgis.jdbc.geometry.Geometry"
// 								converter = "com.cocoa.web.jooq.PostgisGeometryBinding"
// 								includeTypes = "geometry"
// 							}
                            ),
                        )
                    }
                    generate.apply {
                        isRelations = true
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = true
                        isJavaTimeTypes = false
                    }
                    target =
                        org.jooq.meta.jaxb.Target().apply {
                            packageName = "com.cocoa.generated"
                            directory = "build/generated/jooq"
                        }
                }
            }
        }
    }
}

springBoot {
    mainClass.set("com.cocoa.web.WebApplicationKt")
}

tasks.bootRun {
    systemProperty("spring.config.location", "classpath:application.properties")
    jvmArgs = listOf("-Xms1024m", "-Xmx2048m")
}

tasks.bootJar {
    launchScript()
    mainClass.set("com.cocoa.web.WebApplicationKt")
    archiveFileName.set("cocoa.jar")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Delete>("cleanJooq") {
    group = "jooq"
    delete = setOf("build/generated")
}

// In CI we want `compileKotlin` (and therefore `test`) to wait for jOOQ
// codegen so that the generated sources are present before kotlinc runs.
// Locally, this is a no-op unless the user explicitly set the datasource env
// vars (the jOOQ block above will have populated `generateJooq`).
afterEvaluate {
    val hasDatasource = !env["SPRING_DATASOURCE_URL"].isNullOrBlank()
    if (hasDatasource) {
        tasks.named("compileKotlin").configure {
            dependsOn("generateJooq")
        }
    }
}
