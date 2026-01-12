package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Implementation of OnePasswordCommandExecutor that uses ProcessBuilder
 * to execute the actual 1Password CLI commands.
 */
class ProcessOnePasswordCommandExecutor(
    private val logger: Logger,
    private val cliPath: String = "op"
) : OnePasswordCommandExecutor {

    override fun inject(template: String, account: String?, timeout: Int): String {
        logger.debug("Executing op inject with template")

        // Build command
        val command = buildInjectCommand(cliPath, account)

        // Execute with template as stdin
        return executeCommandWithStdin(command, template, timeout)
    }

    private fun buildInjectCommand(cliPath: String, account: String?): List<String> {
        val command = mutableListOf(cliPath, "inject")

        if (account != null) {
            command.add("--account")
            command.add(account)
        }

        return command
    }

    private fun executeCommandWithStdin(command: List<String>, stdin: String, timeout: Int): String {
        logger.debug("Executing command with stdin: ${command.joinToString(" ")}")

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            // Write template to stdin
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(stdin)
                writer.flush()
            }

            val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                throw SecretsException("1Password CLI command timed out after $timeout seconds")
            }

            val exitCode = process.exitValue()

            if (exitCode != 0) {
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream))
                    .readText()
                    .trim()
                throw SecretsException(
                    "1Password CLI command failed with exit code $exitCode: $errorOutput"
                )
            }

            return BufferedReader(InputStreamReader(process.inputStream))
                .readText()
                .trim()
        } catch (e: Exception) {
            if (e is SecretsException) throw e
            throw SecretsException("Failed to execute 1Password CLI command: ${e.message}", e)
        }
    }
}
