package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger

/**
 * A composite resolver that chains multiple SecretsResolver implementations.
 * Tries each resolver in order until one can handle the reference.
 *
 * This resolver supports batch resolution by grouping properties by resolver
 * and delegating to each resolver's batch resolution method.
 */
class CompositeSecretsResolver(
    private val logger: Logger,
    private val resolvers: List<SecretsResolver>
) : SecretsResolver {

    override fun getName(): String = "Composite"

    /**
     * Checks if any resolver can handle the text
     */
    override fun containsReference(text: String): Boolean {
        return resolvers.any { it.containsReference(text) }
    }

    /**
     * Clears cache for all resolvers
     */
    override fun clearCache() {
        resolvers.forEach { resolver ->
            try {
                resolver.clearCache()
                logger.debug("Cleared cache for ${resolver.getName()} resolver")
            } catch (e: Exception) {
                logger.warn("Failed to clear cache for ${resolver.getName()}: ${e.message}")
            }
        }
    }

    /**
     * Resolves references by delegating to component resolvers.
     *
     * Properties are processed through each resolver in sequence, allowing
     * multiple resolvers to handle references in the same property value.
     */
    override fun resolveReferences(properties: Map<String, String>): Map<String, String> {
        if (resolvers.isEmpty()) {
            logger.warn("No secret resolvers available")
            return properties
        }

        if (properties.isEmpty()) {
            return emptyMap()
        }

        // Process properties through each resolver in a sequence
        var currentProperties = properties

        resolvers.forEach { resolver ->
            // Find properties that this resolver can handle
            val propertiesToResolve = currentProperties.filter { (_, value) ->
                resolver.containsReference(value)
            }

            if (propertiesToResolve.isNotEmpty()) {
                try {
                    logger.debug("Resolving ${propertiesToResolve.size} properties with ${resolver.getName()}")
                    val resolved = resolver.resolveReferences(propertiesToResolve)

                    // Update current properties with resolved values
                    currentProperties = currentProperties.toMutableMap().apply {
                        putAll(resolved)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to resolve with ${resolver.getName()}: ${e.message}")
                    throw e
                }
            }
        }

        return currentProperties
    }
}
