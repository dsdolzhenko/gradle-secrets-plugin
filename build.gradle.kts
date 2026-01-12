val gitVersion: groovy.lang.Closure<String> by extra

val githubUsername: String by extra {
    findProperty("githubUsername")?.toString()
        ?: System.getenv("GITHUB_ACTOR")?.ifEmpty { null }
        ?: ""
}

val githubToken: String by extra {
    findProperty("githubToken")?.toString()
        ?: System.getenv("GITHUB_TOKEN")?.ifEmpty { null }
        ?: ""
}

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.palantir.git-version") version "4.2.0"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "io.github.dsdolzhenko"
version = gitVersion()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        register("secrets") {
            id = "io.github.dsdolzhenko.secrets"
            implementationClass = "io.github.dsdolzhenko.secrets.SecretsPlugin"
            description = "Gradle plugin that replaces secret references with actual values from a secret store before task execution"
            tags.addAll("1password", "secrets")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/dsdolzhenko/gradle-secrets-plugin")
            credentials {
                username = githubUsername
                password = githubToken
            }
        }
    }
}