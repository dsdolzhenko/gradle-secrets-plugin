package io.github.dsdolzhenko.secrets

interface SecretsResolver {
    /**
     * Resolves references in a given text
     */
    fun resolveReferences(text: String): String

    /**
     * Checks if the string contains a secret reference relevant to the resolver
     */
    fun containsReference(text: String): Boolean

    /**
     * Clears the internal cache of resolved secrets
     * Default implementation does nothing for resolvers without caching
     */
    fun clearCache() {
        // Default no-op implementation
    }

    /**
     * Name of this resolver implementation (for logging and debugging)
     */
    fun getName(): String
}