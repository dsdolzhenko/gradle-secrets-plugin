package io.github.dsdolzhenko.secrets

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [SecretsInjector.injectProjectProperties] through a real Gradle build (rather than
 * ProjectBuilder, which does not load gradle.properties into project properties the way a real
 * build does), to confirm the provider-based property scan replacing the deprecated
 * `project.properties` still resolves secrets from both gradle.properties and `ext`.
 */
class SecretsPluginProjectPropertiesFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val buildFile: File
        get() = projectDir.resolve("build.gradle.kts")

    private val settingsFile: File
        get() = projectDir.resolve("settings.gradle.kts")

    private val gradlePropertiesFile: File
        get() = projectDir.resolve("gradle.properties")

    private fun fakeOpExecutable(resolvedJson: String): File {
        val script = projectDir.resolve("fake-op.sh")
        script.writeText(
            """
            #!/bin/sh
            cat >/dev/null
            echo '$resolvedJson'
            """.trimIndent(),
        )
        script.setExecutable(true)
        return script
    }

    @Test
    fun `resolves secret references from gradle properties and ext properties`() {
        settingsFile.writeText("")

        gradlePropertiesFile.writeText("dbPassword=op://vault/item/password\n")

        val fakeOp = fakeOpExecutable("""{"dbPassword":"resolved-secret-value","apiKey":"resolved-secret-value"}""")

        buildFile.writeText(
            """
            plugins {
                id("io.github.dsdolzhenko.secrets")
            }

            secrets {
                cliPath.set("${fakeOp.absolutePath.replace("\\", "\\\\")}")
            }

            extra["apiKey"] = "op://vault/item/key"

            tasks.register("printProperties") {
                doLast {
                    println("DB_PASSWORD_CHECK:" + project.property("dbPassword"))
                    println("API_KEY_CHECK:" + project.extensions.extraProperties.get("apiKey"))
                }
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withArguments("printProperties")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printProperties")?.outcome)
        assertTrue(result.output.contains("DB_PASSWORD_CHECK:resolved-secret-value"))
        assertTrue(result.output.contains("API_KEY_CHECK:resolved-secret-value"))
    }
}
