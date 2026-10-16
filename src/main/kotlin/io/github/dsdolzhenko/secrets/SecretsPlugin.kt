package io.github.dsdolzhenko.secrets

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
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

            // Capture plain config values now: task actions registered below run under the
            // configuration cache, which forbids reaching back into Project/Extension/Property.
            val excludedTasks = extension.excludedTasks.get()
            val injectSystemProperties = extension.injectSystemProperties.get()
            val injectEnvironmentVariables = extension.injectEnvironmentVariables.get()
            val injectProjectProperties = extension.injectProjectProperties.get()
            val failOnError = extension.failOnError.get()
            val clearSecretsAfterTask = extension.clearSecretsAfterTask.get()

            val secretsInjector = SecretsInjector(
                logger = Logging.getLogger(SecretsInjector::class.java),
                secretsResolver = secretsResolver,
                includedProperties = extension.includedProperties.get(),
                excludedProperties = extension.excludedProperties.get(),
                verbose = extension.verbose.get()
            )

            // Project properties can only be mutated at configuration time, so this runs
            // once here rather than from a (configuration-cache-incompatible) per-task action.
            if (injectProjectProperties) {
                try {
                    secretsInjector.injectProjectProperties(project)
                } catch (e: Exception) {
                    if (failOnError) {
                        throw SecretsException("Failed to inject secrets into project properties", e)
                    } else {
                        project.logger.warn("Failed to inject secrets into project properties: ${e.message}")
                    }
                }
            }

            project.tasks.configureEach { task ->
                if (task.name in excludedTasks) {
                    return@configureEach
                }

                task.doFirst {
                    try {
                        if (injectSystemProperties) {
                            secretsInjector.injectSystemProperties()
                        }

                        if (injectEnvironmentVariables) {
                            secretsInjector.injectEnvironmentVariables(it)
                        }
                    } catch (e: Exception) {
                        if (failOnError) {
                            throw SecretsException("Failed to inject secrets", e)
                        } else {
                            it.logger.warn("Failed to inject secrets: ${e.message}")
                        }
                    }
                }

                task.doLast {
                    if (clearSecretsAfterTask) {
                        secretsInjector.clearInjectedSecrets(it)
                    }
                }
            }

            // Add a cleanup task to clear cached secrets
            project.tasks.register("clearSecretsCache") {
                it.group = "secrets"
                it.description = "Clears cached secrets from the resolver"
                it.doLast {
                    secretsResolver.clearCache()
                    it.logger.lifecycle("Secrets cache cleared")
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
            return defaultOnePasswordResolver(project, extension)
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
            return defaultOnePasswordResolver(project, extension)
        }

        logger.info("Active secret resolvers:")
        resolvers.forEach { resolver ->
            logger.info("  - ${resolver.getName()}")
        }

        // Return composite resolver if multiple, or single resolver if only one
        return if (resolvers.size == 1) {
            resolvers.first()
        } else {
            CompositeSecretsResolver(Logging.getLogger(CompositeSecretsResolver::class.java), resolvers)
        }
    }

    private fun defaultOnePasswordResolver(project: Project, extension: SecretsExtension): SecretsResolver {
        val logger = Logging.getLogger(SecretsOnePasswordResolver::class.java)
        val commandExecutor = ProcessOnePasswordCommandExecutor(logger, extension.cliPath.get(), project.providers)
        return SecretsOnePasswordResolver(
            logger = logger,
            commandExecutor = commandExecutor,
            account = extension.account.orNull,
            timeout = extension.cliTimeout.get()
        )
    }
}
