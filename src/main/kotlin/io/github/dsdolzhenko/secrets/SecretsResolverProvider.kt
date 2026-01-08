package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Service Provider Interface for SecretsResolver implementations.
 * Implementations of this interface are discovered automatically via Java ServiceLoader.
 *
 * To register a provider, create a file:
 * META-INF/services/io.github.dsdolzhenko.secrets.SecretsResolverProvider
 *
 * containing the fully qualified class name of your provider implementation.
 */
interface SecretsResolverProvider {
    /**
     * Creates a SecretsResolver instance configured for the given project.
     *
     * @param project The Gradle project
     * @param extension The secrets extension with configuration
     * @return A configured SecretsResolver instance, or null if this provider
     *         cannot provide a resolver for the current configuration
     */
    fun createResolver(project: Project, extension: SecretsExtension): SecretsResolver?

    /**
     * Name of this provider (for logging and debugging)
     */
    fun getName(): String
}
