import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.6.2")
        classpath("org.postgresql:postgresql:42.7.8")
    }
}

plugins {
    base
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.flywaydb.flyway") version "12.6.2"
    id("com.diffplug.spotless") version "8.6.0"
}

group = "com.settle"
version = "0.0.1-SNAPSHOT"
description = "Payment settlement core"

val ktlintVersion = "1.8.0"
val kotlinVersion = "2.2.21"

flyway {
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/settle_core"
    user = System.getenv("DB_USERNAME") ?: "settle"
    password = System.getenv("DB_PASSWORD") ?: "settle"
    defaultSchema = "migration"
    schemas = arrayOf("migration", "merchant", "payment", "settlement")
    locations = arrayOf("filesystem:libs/persistence/src/main/resources/db/migration")
    cleanDisabled = true
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)

            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            }
        }

        dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }

    pluginManager.withPlugin("io.spring.dependency-management") {
        extensions.configure<DependencyManagementExtension> {
            imports {
                mavenBom(SpringBootPlugin.BOM_COORDINATES)
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        rootProject.tasks.named("check") {
            dependsOn(this@configureEach)
        }
    }
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "ktlint_official",
                ),
            )
    }

    kotlinGradle {
        target("*.gradle.kts", "apps/**/*.gradle.kts", "domains/**/*.gradle.kts", "libs/**/*.gradle.kts")
        ktlint(ktlintVersion)
            .editorConfigOverride(
                mapOf(
                    "ktlint_code_style" to "ktlint_official",
                ),
            )
    }

    format("misc") {
        target(
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
            ".mise.toml",
            "*.md",
            "*.yml",
            "*.yaml",
            "docs/**/*.md",
            "**/src/**/*.sql",
        )
        targetExclude(".gradle/**", ".idea/**", "**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
