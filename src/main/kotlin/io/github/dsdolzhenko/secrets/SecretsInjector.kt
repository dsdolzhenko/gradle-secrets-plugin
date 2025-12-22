package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logger
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
    private val injectedProperties = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * Injects secrets into system properties
     */
    fun injectSystemProperties() {
        val systemProperties = System.getProperties()
        val propertiesToInject = mutableMapOf<String, String>()
        
        systemProperties.stringPropertyNames().forEach { propertyName ->
            if (shouldProcessProperty(propertyName, extension)) {
                val value = systemProperties.getProperty(propertyName)
                if (secretsResolver.containsReference(value)) {
                    try {
                        // Store original value
                        originalValues.putIfAbsent(propertyName, value)
                        
                        // Resolve and inject
                        val resolvedValue = secretsResolver.resolveReferences(value)
                        
                        propertiesToInject[propertyName] = resolvedValue
                        
                        if (extension.verbose.get()) {
                            logger.lifecycle(
                                "Injected secret into system property: $propertyName"
                            )
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to resolve secret for system property $propertyName: ${e.message}"
                        )
                        throw e
                    }
                }
            }
        }
        
        // Apply all changes at once
        propertiesToInject.forEach { (name, value) ->
            System.setProperty(name, value)
        }
        
        if (propertiesToInject.isNotEmpty()) {
            logger.debug(
                "Injected ${propertiesToInject.size} 1Password secrets into system properties"
            )
        }
    }
    
    /**
     * Injects secrets into environment variables for a task
     * Note: We can't modify actual env vars, but we can modify task environment
     */
    fun injectEnvironmentVariables(task: Task) {
        // Try to access environment property if the task supports it
        val envProperty = try {
            task.javaClass.getMethod("getEnvironment").invoke(task) as? MutableMap<String, Any>
        } catch (e: Exception) {
            null
        }
        
        if (envProperty != null) {
            val injectedVars = mutableSetOf<String>()
            val envsToInject = mutableMapOf<String, String>()
            
            envProperty.forEach { (name, value) ->
                if (value is String && shouldProcessProperty(name, extension)) {
                    if (secretsResolver.containsReference(value)) {
                        try {
                            val resolvedValue = secretsResolver.resolveReferences(value)
                            
                            envsToInject[name] = resolvedValue
                            injectedVars.add(name)
                            
                            if (extension.verbose.get()) {
                                logger.lifecycle(
                                    "Injected secret into environment variable: $name"
                                )
                            }
                        } catch (e: Exception) {
                            logger.error(
                                "Failed to resolve secret for environment variable $name: ${e.message}"
                            )
                            throw e
                        }
                    }
                }
            }
            
            // Apply changes
            envProperty.putAll(envsToInject)
            injectedProperties[task.path] = injectedVars
            
            if (envsToInject.isNotEmpty()) {
                logger.debug(
                    "Injected ${envsToInject.size} secrets into task environment"
                )
            }
        } else {
            logger.debug("Task ${task.name} does not support environment property injection")
        }
    }
    
    /**
     * Injects secrets into project properties
     */
    fun injectProjectProperties() {
        val propertiesToInject = mutableMapOf<String, Any>()
        
        project.properties.forEach { (propertyName, value) ->
            if (value is String && shouldProcessProperty(propertyName, extension)) {
                if (secretsResolver.containsReference(value)) {
                    try {
                        // Store original value
                        originalValues.putIfAbsent(propertyName, value)
                        
                        // Resolve and inject
                        val resolvedValue = secretsResolver.resolveReferences(value)
                        
                        propertiesToInject[propertyName] = resolvedValue
                        
                        if (extension.verbose.get()) {
                            logger.lifecycle(
                                "Injected secret into project property: $propertyName"
                            )
                        }
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to resolve secret for project property $propertyName: ${e.message}"
                        )
                        throw e
                    }
                }
            }
        }
        
        // Note: Project properties are typically read-only after initialization
        // This will work for extra properties set via ext
        propertiesToInject.forEach { (name, value) ->
            if (project.hasProperty(name)) {
                project.extensions.extraProperties.set(name, value)
            }
        }
        
        if (propertiesToInject.isNotEmpty()) {
            logger.debug(
                "Injected ${propertiesToInject.size} secrets into project properties"
            )
        }
    }
    
    /**
     * Clears injected secrets from the task
     */
    fun clearInjectedSecrets(task: Task) {
        val injectedVars = injectedProperties.remove(task.path) ?: return
        
        val envProperty = try {
            task.javaClass.getMethod("getEnvironment").invoke(task) as? MutableMap<String, Any>
        } catch (e: Exception) {
            null
        }
        
        envProperty?.let { env ->
            injectedVars.forEach { varName ->
                env.remove(varName)
            }
        }
        
        logger.debug("Cleared ${injectedVars.size} injected secrets from task ${task.name}")
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
    
    /**
     * Restores original property values
     */
    fun restoreOriginalValues() {
        originalValues.forEach { (name, value) ->
            try {
                System.setProperty(name, value)
                logger.debug("Restored original value for property: $name")
            } catch (e: Exception) {
                logger.warn("Failed to restore original value for property $name: ${e.message}")
            }
        }
        originalValues.clear()
    }
}