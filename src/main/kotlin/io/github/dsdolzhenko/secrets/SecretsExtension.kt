package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Extension for configuring the Secrets plugin
 */
abstract class SecretsExtension(private val project: Project) {
    /**
     * Enable or disable the plugin. Default: true
     */
    abstract val enabled: Property<Boolean>

    /**
     * Inject secrets into system properties. Default: true
     */
    abstract val injectSystemProperties: Property<Boolean>

    /**
     * Inject secrets into environment variables. Default: true
     */
    abstract val injectEnvironmentVariables: Property<Boolean>

    /**
     * Inject secrets into project properties. Default: true
     */
    abstract val injectProjectProperties: Property<Boolean>

    /**
     * Fail the build if secret resolution fails. Default: true
     */
    abstract val failOnError: Property<Boolean>

    /**
     * Clear secrets from memory after each task. Default: false
     * (Secrets are cached during the build for performance)
     */
    abstract val clearSecretsAfterTask: Property<Boolean>

    /**
     * Tasks to exclude from secret injection
     */
    abstract val excludedTasks: SetProperty<String>

    /**
     * Path to 1Password CLI executable. Default: "op"
     */
    abstract val cliPath: Property<String>

    /**
     * 1Password account identifier (optional)
     * If not set, uses the currently signed-in account
     */
    abstract val account: Property<String>

    /**
     * Pattern for detecting 1Password references
     * Default: op://[vault]/[item]/[field]
     */
    abstract val referencePattern: Property<String>

    /**
     * Timeout for 1Password CLI calls in seconds. Default: 30
     */
    abstract val cliTimeout: Property<Int>

    /**
     * Properties to explicitly include for injection
     * If empty, all properties with references are processed
     */
    abstract val includedProperties: SetProperty<String>

    /**
     * Properties to explicitly exclude from injection
     */
    abstract val excludedProperties: SetProperty<String>

    /**
     * Enable verbose logging for debugging. Default: false
     */
    abstract val verbose: Property<Boolean>

    init {
        // Set defaults
        enabled.convention(true)
        injectSystemProperties.convention(true)
        injectEnvironmentVariables.convention(true)
        injectProjectProperties.convention(true)
        failOnError.convention(true)
        clearSecretsAfterTask.convention(false)
        cliPath.convention("op")
        referencePattern.convention("op://[^\\s]+")
        cliTimeout.convention(30)
        verbose.convention(false)
    }

    /**
     * Convenience method to exclude tasks
     */
    fun excludeTasks(vararg taskNames: String) {
        excludedTasks.addAll(*taskNames)
    }

    /**
     * Convenience method to include properties
     */
    fun includeProperties(vararg propertyNames: String) {
        includedProperties.addAll(*propertyNames)
    }

    /**
     * Convenience method to exclude properties
     */
    fun excludeProperties(vararg propertyNames: String) {
        excludedProperties.addAll(*propertyNames)
    }
}