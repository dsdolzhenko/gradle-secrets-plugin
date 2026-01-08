package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger

/**
 * A composite resolver that chains multiple SecretsResolver implementations.
 * Tries each resolver in order until one can handle the reference.
 */
class CompositeSecretsResolver(
    private val logger: Logger,
    private val resolvers: List<SecretsResolver>
) : SecretsResolver {

    override fun getName(): String = "Composite"

    /**
     * Resolves references by trying each resolver in order
     */
    override fun resolveReferences(text: String): String {
        if (resolvers.isEmpty()) {
            logger.warn("No secret resolvers available")
            return text
        }

        var result = text
        var modified = false

        // Try each resolver that claims to have references in the text
        for (resolver in resolvers) {
            if (resolver.containsReference(result)) {
                try {
                    val previousResult = result
                    result = resolver.resolveReferences(result)
                    if (result != previousResult) {
                        modified = true
                        logger.debug("Resolved references using ${resolver.getName()} resolver")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to resolve references with ${resolver.getName()}: ${e.message}")
                    throw e
                }
            }
        }

        if (!modified && containsReference(text)) {
            logger.warn("Text contains secret references but no resolver could handle them")
        }

        return result
    }

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
     * Returns the list of active resolvers
     */
    fun getResolvers(): List<SecretsResolver> = resolvers.toList()
}
