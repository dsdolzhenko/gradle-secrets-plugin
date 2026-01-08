package io.github.dsdolzhenko.secrets

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.ServiceLoader

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

            // Discover all resolver providers via ServiceLoader
            val secretsResolver = discoverAndCreateResolvers(project, extension)

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
            project.tasks.register("clearSecretsCache") {
                it.group = "secrets"
                it.description = "Clears cached secrets from the resolver"
                it.doLast {
                    secretsResolver.clearCache()
                    project.logger.lifecycle("Secrets cache cleared")
                }
            }
        }
    }

    /**
     * Discovers all SecretsResolverProvider implementations via ServiceLoader
     * and creates a composite resolver from them.
     */
    private fun discoverAndCreateResolvers(project: Project, extension: SecretsExtension): SecretsResolver {
        val logger = project.logger

        // Load all provider implementations via ServiceLoader
        val providers = ServiceLoader.load(
            SecretsResolverProvider::class.java,
            SecretsResolverProvider::class.java.classLoader
        ).toList()

        if (providers.isEmpty()) {
            logger.warn("No SecretsResolverProvider implementations found via ServiceLoader")
            logger.warn("Falling back to default 1Password resolver")
            return SecretsOnePasswordResolver(
                logger,
                extension.cliPath.get(),
                extension.account.orNull,
                extension.cliTimeout.get()
            )
        }

        logger.info("Discovered ${providers.size} secret resolver provider(s):")
        providers.forEach { provider ->
            logger.info("  - ${provider.getName()}")
        }

        // Create resolver instances from each provider
        val resolvers = providers.mapNotNull { provider ->
            try {
                provider.createResolver(project, extension)?.also { resolver ->
                    logger.debug("Created ${resolver.getName()} resolver from ${provider.getName()} provider")
                }
            } catch (e: Exception) {
                logger.warn("Failed to create resolver from ${provider.getName()} provider: ${e.message}")
                null
            }
        }

        if (resolvers.isEmpty()) {
            logger.warn("No resolvers could be created from providers")
            logger.warn("Falling back to default 1Password resolver")
            return SecretsOnePasswordResolver(
                logger,
                extension.cliPath.get(),
                extension.account.orNull,
                extension.cliTimeout.get()
            )
        }

        logger.info("Active secret resolvers:")
        resolvers.forEach { resolver ->
            logger.info("  - ${resolver.getName()}")
        }

        // Return composite resolver if multiple, or single resolver if only one
        return if (resolvers.size == 1) {
            resolvers.first()
        } else {
            CompositeSecretsResolver(logger, resolvers)
        }
    }
}