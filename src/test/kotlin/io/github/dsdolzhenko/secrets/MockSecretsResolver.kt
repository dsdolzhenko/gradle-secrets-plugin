package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger

/**
 * Mock implementation of SecretsResolver for testing purposes
 * Allows pre-defining secret references and their resolved values
 */
class MockSecretsResolver(
    private val logger: Logger,
    private val secretsMap: Map<String, String> = emptyMap(),
    private val referencePrefix: String = "mock://"
) : SecretsResolver {

    private val resolvedSecrets = mutableListOf<String>()
    private var cacheCleared = false

    override fun getName(): String = "Mock"

    /**
     * Resolves mock references in the format: mock://secret-name
     */
    override fun resolveReferences(text: String): String {
        var result = text
        secretsMap.forEach { (reference, value) ->
            val fullReference = "$referencePrefix$reference"
            if (result.contains(fullReference)) {
                result = result.replace(fullReference, value)
                resolvedSecrets.add(reference)
                logger.debug("Resolved mock reference: $reference")
            }
        }
        return result
    }

    /**
     * Checks if text contains mock references
     */
    override fun containsReference(text: String): Boolean {
        return text.contains(referencePrefix)
    }

    /**
     * Clears the cache and tracks that it was cleared
     */
    override fun clearCache() {
        cacheCleared = true
        resolvedSecrets.clear()
        logger.debug("Mock resolver cache cleared")
    }

    /**
     * Returns the list of secrets that were resolved
     */
    fun getResolvedSecrets(): List<String> = resolvedSecrets.toList()

    /**
     * Returns whether the cache was cleared
     */
    fun wasCacheCleared(): Boolean = cacheCleared
}
