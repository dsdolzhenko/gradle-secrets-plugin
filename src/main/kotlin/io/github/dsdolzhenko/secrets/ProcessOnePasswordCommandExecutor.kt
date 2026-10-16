package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Implementation of OnePasswordCommandExecutor that uses ProcessBuilder
 * to execute the actual 1Password CLI commands.
 *
 * The process is started from inside a [ValueSource] rather than directly from
 * [inject]: starting an external process is only permitted during project
 * configuration when it happens inside a ValueSource, and a ValueSource works
 * equally well when invoked from a task action, so routing every invocation
 * through it keeps this executor usable both at configuration time (e.g. for
 * project properties injection) and at task execution time, under the
 * configuration cache.
 */
class ProcessOnePasswordCommandExecutor(
    private val logger: Logger,
    private val cliPath: String = "op",
    private val providers: ProviderFactory
) : OnePasswordCommandExecutor {

    override fun inject(template: String, account: String?, timeout: Int): String {
        logger.debug("Executing op inject with template")

        return try {
            providers.of(OpInjectValueSource::class.java) { spec ->
                spec.parameters.cliPath.set(cliPath)
                spec.parameters.account.set(account)
                spec.parameters.timeoutSeconds.set(timeout)
                spec.parameters.template.set(template)
            }.get()
        } catch (e: Exception) {
            if (e is SecretsException) throw e
            throw SecretsException("Failed to execute 1Password CLI command: ${e.message}", e)
        }
    }

    interface OpInjectParameters : ValueSourceParameters {
        val cliPath: Property<String>
        val account: Property<String>
        val timeoutSeconds: Property<Int>
        val template: Property<String>
    }

    abstract class OpInjectValueSource : ValueSource<String, OpInjectParameters> {
        override fun obtain(): String {
            val command = buildInjectCommand(parameters.cliPath.get(), parameters.account.orNull)
            return executeCommandWithStdin(command, parameters.template.get(), parameters.timeoutSeconds.get())
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
}
