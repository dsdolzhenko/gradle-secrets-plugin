package io.github.dsdolzhenko.secrets

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that replaces secret references with actual values
 * from a secret store before task execution.
 */
class SecretsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create the extension for configuration
        val extension = project.extensions.create(
            "secrets",
            SecretsExtension::class.java
        )

        // Configure after project evaluation to ensure the extension is configured
        project.afterEvaluate {

            // Initialize the secret resolver with configured values
            val secretsResolver = SecretsOnePasswordResolver(
                project.logger,
                extension.cliPath.get(),
                extension.account.orNull,
                extension.cliTimeout.get()
            )

            // Register task execution listener
            project.gradle.taskGraph.addTaskExecutionListener(
                SecretsTaskExecutionListener(
                    project.logger,
                    extension,
                    SecretsInjector(
                        project,
                        project.logger,
                        extension,
                        secretsResolver
                    )
                )
            )

            // Add a cleanup task to clear cached secrets
            project.tasks.register("clearOnePasswordCache") {
                it.group = "1Password"
                it.description = "Clears cached 1Password secrets"
                it.doLast {
                    secretsResolver.clearCache()
                    project.logger.lifecycle("1Password secret cache cleared")
                }
            }
        }
    }
}