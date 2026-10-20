package io.github.dsdolzhenko.secrets

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretsSettingsPluginTest {

    @TempDir
    lateinit var projectDir: File

    private val settingsFile: File
        get() = projectDir.resolve("settings.gradle.kts")

    private val buildFile: File
        get() = projectDir.resolve("build.gradle.kts")

    private val envFile: File
        get() = projectDir.resolve(".env")

    private fun fakeOpExecutable(resolvedJson: String): File {
        val script = projectDir.resolve("fake-op.sh")
        script.writeText(
            """
            #!/bin/sh
            cat >/dev/null
            echo '$resolvedJson'
            """.trimIndent()
        )
        script.setExecutable(true)
        return script
    }

    @Test
    fun `settings plugin resolves secret references read through the dotenv extension`() {
        envFile.writeText(
            """
            TOKEN=op://vault/item/field
            """.trimIndent()
        )

        val fakeOp = fakeOpExecutable("""{"value":"resolved-secret-value"}""")

        settingsFile.writeText(
            """
            plugins {
                id("io.github.dsdolzhenko.secrets.settings")
            }

            secrets {
                cliPath.set("${fakeOp.absolutePath.replace("\\", "\\\\")}")
            }
            """.trimIndent()
        )

        buildFile.writeText(
            """
            val token: String = dotenv["TOKEN"] ?: ""

            tasks.register("printToken") {
                doLast {
                    println("TOKEN_CHECK:${'$'}token")
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("printToken")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printToken")?.outcome)
        assertTrue(result.output.contains("TOKEN_CHECK:resolved-secret-value"))
    }

    @Test
    fun `settings plugin leaves plain dotenv values untouched`() {
        envFile.writeText(
            """
            PLAIN=hello
            """.trimIndent()
        )

        settingsFile.writeText(
            """
            plugins {
                id("io.github.dsdolzhenko.secrets.settings")
            }
            """.trimIndent()
        )

        buildFile.writeText(
            """
            val plain: String = dotenv["PLAIN"] ?: ""

            tasks.register("printPlain") {
                doLast {
                    println("PLAIN_CHECK:${'$'}plain")
                }
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("printPlain")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printPlain")?.outcome)
        assertTrue(result.output.contains("PLAIN_CHECK:hello"))
    }
}
