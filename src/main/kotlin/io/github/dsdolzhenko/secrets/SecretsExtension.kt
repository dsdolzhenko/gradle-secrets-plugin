package io.github.dsdolzhenko.secrets

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Extension for configuring the Secrets plugin
 */
abstract class SecretsExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Enable or disable the plugin. Default: true
     */
    val enabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Inject secrets into system properties. Default: true
     */
    val injectSystemProperties: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Inject secrets into environment variables. Default: true
     */
    val injectEnvironmentVariables: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Inject secrets into project properties. Default: true
     */
    val injectProjectProperties: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Fail the build if secret resolution fails. Default: true
     */
    val failOnError: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Clear secrets from memory after each task. Default: false
     * (Secrets are cached during the build for performance)
     */
    val clearSecretsAfterTask: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /**
     * Tasks to exclude from secret injection
     */
    val excludedTasks: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * Path to 1Password CLI executable. Default: "op"
     */
    val cliPath: Property<String> = objects.property(String::class.java)
        .convention("op")

    /**
     * 1Password account identifier (optional)
     * If not set, uses the currently signed-in account
     */
    val account: Property<String> = objects.property(String::class.java)

    /**
     * Pattern for detecting 1Password references
     * Default: op://[vault]/[item]/[field]
     */
    val referencePattern: Property<String> = objects.property(String::class.java)
        .convention("op://[^\\s]+")

    /**
     * Timeout for 1Password CLI calls in seconds. Default: 30
     */
    val cliTimeout: Property<Int> = objects.property(Int::class.java)
        .convention(30)

    /**
     * Properties to explicitly include for injection
     * If empty, all properties with references are processed
     */
    val includedProperties: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * Properties to explicitly exclude from injection
     */
    val excludedProperties: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * Enable verbose logging for debugging. Default: false
     */
    val verbose: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

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