package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
import org.gradle.process.ProcessForkOptions
import java.util.concurrent.ConcurrentHashMap

/**
 * Injects resolved secrets into properties and environment variables
 */
class SecretsInjector(
    private val project: Project,
    private val logger: Logger,
    private val extension: SecretsExtension,
    private val secretsResolver: SecretsResolver
) {

    private val originalValues = ConcurrentHashMap<String, String>()
    private val injectedVariables = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * Injects secrets into system properties
     */
    fun injectSystemProperties() {
        val systemProperties = System.getProperties()

        // Collect all properties with secrets
        val propertiesToResolve = mutableMapOf<String, String>()

        systemProperties.stringPropertyNames().forEach { propertyName ->
            if (shouldProcessProperty(propertyName, extension)) {
                val value = systemProperties.getProperty(propertyName)
                if (secretsResolver.containsReference(value)) {
                    // Store original value
                    originalValues.putIfAbsent(propertyName, value)
                    propertiesToResolve[propertyName] = value
                }
            }
        }

        if (propertiesToResolve.isEmpty()) {
            return
        }

        try {
            // Use batch resolution
            logger.debug("Resolving ${propertiesToResolve.size} system properties")
            val resolvedProperties = secretsResolver.resolveReferences(propertiesToResolve)

            // Apply all changes at once
            resolvedProperties.forEach { (name, value) ->
                System.setProperty(name, value)

                if (extension.verbose.get()) {
                    logger.lifecycle("Injected secret into system property: $name")
                }
            }

            logger.debug(
                "Injected ${resolvedProperties.size} secrets into system properties"
            )
        } catch (e: Exception) {
            logger.error("Failed to resolve secrets for system properties: ${e.message}")
            throw e
        }
    }

    /**
     * Injects secrets into environment variables for a task
     * Note: We can't modify actual environment variables, but we can modify the task environment
     */
    fun injectEnvironmentVariables(task: Task) {
        if (task !is ProcessForkOptions) {
            logger.debug("Task ${task.name} does not support environment property injection")
            return
        }

        val environment = task.environment.orEmpty().toMutableMap()

        // Collect all environment variables with secrets
        val variablesToResolve = mutableMapOf<String, String>()

        environment.forEach { (name, value) ->
            if (value is String && shouldProcessProperty(name, extension) && secretsResolver.containsReference(value)) {
                variablesToResolve[name] = value
            }
        }

        if (variablesToResolve.isEmpty()) {
            return
        }

        try {
            // Use batch resolution
            logger.debug("Resolving ${variablesToResolve.size} environment variables")
            val resolvedVariables = secretsResolver.resolveReferences(variablesToResolve)

            // Apply changes
            environment.putAll(resolvedVariables)
            injectedVariables[task.path] = resolvedVariables.keys.toMutableSet()

            if (extension.verbose.get()) {
                resolvedVariables.keys.forEach { name ->
                    logger.lifecycle("Injected secret into environment variable: $name")
                }
            }

            logger.debug(
                "Injected ${resolvedVariables.size} secrets into task environment"
            )

            task.environment = environment
        } catch (e: Exception) {
            logger.error("Failed to resolve secrets for environment variables: ${e.message}")
            throw e
        }
    }

    /**
     * Injects secrets into project properties
     */
    fun injectProjectProperties() {
        // Collect all project properties with secrets
        val propertiesToResolve = mutableMapOf<String, String>()

        project.properties.forEach { (propertyName, value) ->
            if (value is String && shouldProcessProperty(propertyName, extension)) {
                if (secretsResolver.containsReference(value)) {
                    // Store original value
                    originalValues.putIfAbsent(propertyName, value)
                    propertiesToResolve[propertyName] = value
                }
            }
        }

        if (propertiesToResolve.isEmpty()) {
            return
        }

        try {
            // Use batch resolution
            logger.debug("Resolving ${propertiesToResolve.size} project properties")
            val resolvedProperties = secretsResolver.resolveReferences(propertiesToResolve)

            // Note: Project properties are typically read-only after initialization
            // This will work for extra properties set via ext
            resolvedProperties.forEach { (name, value) ->
                if (project.hasProperty(name)) {
                    project.extensions.extraProperties.set(name, value)
                }

                if (extension.verbose.get()) {
                    logger.lifecycle("Injected secret into project property: $name")
                }
            }

            logger.debug(
                "Injected ${resolvedProperties.size} secrets into project properties"
            )
        } catch (e: Exception) {
            logger.error("Failed to resolve secrets for project properties: ${e.message}")
            throw e
        }
    }

    /**
     * Clears injected secrets from the task
     */
    fun clearInjectedSecrets(task: Task) {
        if (task !is ProcessForkOptions) {
            logger.debug("Task ${task.name} does not support environment property injection")
            return
        }

        val injectedVariables = this@SecretsInjector.injectedVariables.remove(task.path) ?: return
        val environment = task.environment.orEmpty().toMutableMap()

        injectedVariables.forEach { varName ->
            environment.remove(varName)
        }

        logger.debug("Cleared ${injectedVariables.size} injected secrets from task ${task.name}")
    }

    /**
     * Determines if a property should be processed based on include/exclude lists
     */
    private fun shouldProcessProperty(propertyName: String, extension: SecretsExtension): Boolean {
        // Check explicit exclusions first
        if (extension.excludedProperties.get().contains(propertyName)) {
            return false
        }

        // If the include list is specified, property must be in it
        val includeList = extension.includedProperties.get()
        return !(includeList.isNotEmpty() && !includeList.contains(propertyName))
    }
}