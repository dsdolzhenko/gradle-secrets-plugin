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
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.palantir.git-version") version "5.0.0"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "io.github.dsdolzhenko"
version = gitVersion()

// Version of the companion dotenv plugin that first exposes
// DotEnvSettingsExtension.resolveValuesWith, which the secrets settings plugin hooks into.
val dotenvPluginVersion = "0.3.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    compileOnly("io.github.dsdolzhenko:gradle-dotenv-plugin:$dotenvPluginVersion")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    website = "https://github.com/dsdolzhenko/gradle-secrets-plugin"
    vcsUrl = "https://github.com/dsdolzhenko/gradle-secrets-plugin"

    plugins {
        register("secrets") {
            id = "io.github.dsdolzhenko.secrets"
            implementationClass = "io.github.dsdolzhenko.secrets.SecretsPlugin"
            displayName = "Gradle Secrets Plugin"
            description = "Gradle plugin that replaces secret references with actual values from a secret store before task execution"
            tags.addAll("secrets", "environment", "security")
        }

        register("secrets-settings") {
            id = "io.github.dsdolzhenko.secrets.settings"
            implementationClass = "io.github.dsdolzhenko.secrets.SecretsSettingsPlugin"
            displayName = "Gradle Secrets Settings Plugin"
            description = "Resolves 1Password secret references in values read via the dotenv settings plugin's extension"
            tags.addAll("secrets", "environment", "security")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// GradleRunner.withPluginClasspath() otherwise only sees the main source set's runtime
// classpath, which excludes the dotenv-plugin dependency the functional tests need in order
// to apply io.github.dsdolzhenko.dotenv.settings by id. Resolved via its own configuration
// (rather than testRuntimeClasspath) to avoid a cycle through this task's own output, which
// testRuntimeClasspath includes.
val dotenvPluginRuntime: Configuration by configurations.creating
dependencies {
    dotenvPluginRuntime("io.github.dsdolzhenko:gradle-dotenv-plugin:$dotenvPluginVersion")
}
tasks.named<org.gradle.plugin.devel.tasks.PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(dotenvPluginRuntime)
}

publishing {
    repositories {
        mavenLocal()
    }
}
