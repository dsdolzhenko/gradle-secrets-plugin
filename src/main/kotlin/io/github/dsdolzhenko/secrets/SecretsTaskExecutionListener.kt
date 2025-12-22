package io.github.dsdolzhenko.secrets

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskState

/**
 * Listener that injects secret values before task execution
 */
class SecretsTaskExecutionListener(
    private val logger: Logger,
    private val extension: SecretsExtension,
    private val secretsInjector: SecretsInjector,
) : TaskExecutionListener {

    override fun beforeExecute(task: Task) {
        if (!extension.enabled.get()) {
            return
        }

        // Skip if the task is in the exclusion list
        if (extension.excludedTasks.get().contains(task.name)) {
            logger.debug("Skipping secrets injection for excluded task: ${task.name}")
            return
        }

        try {
            logger.debug("Injecting secrets for task: ${task.name}")

            // Inject into system properties
            if (extension.injectSystemProperties.get()) {
                secretsInjector.injectSystemProperties()
            }

            // Inject into environment variables
            if (extension.injectEnvironmentVariables.get()) {
                secretsInjector.injectEnvironmentVariables(task)
            }

            // Inject into project properties
            if (extension.injectProjectProperties.get()) {
                secretsInjector.injectProjectProperties()
            }

        } catch (e: Exception) {
            val failOnError = extension.failOnError.get()
            if (failOnError) {
                throw SecretsException("Failed to inject secrets", e)
            } else {
                logger.warn("Failed to inject secrets: ${e.message}")
            }
        }
    }

    override fun afterExecute(task: Task, state: TaskState) {
        // Cleanup if needed
        if (extension.clearSecretsAfterTask.get()) {
            secretsInjector.clearInjectedSecrets(task)
        }
    }
}
