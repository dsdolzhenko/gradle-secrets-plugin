val gitVersion: groovy.lang.Closure<String> by extra

plugins {
    kotlin("jvm") version "2.3.0"
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
    }
}