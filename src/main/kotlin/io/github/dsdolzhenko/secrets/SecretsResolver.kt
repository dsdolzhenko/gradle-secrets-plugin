package io.github.dsdolzhenko.secrets

interface SecretsResolver {
    /**
     * Resolves all secret references in a batch of properties.
     *
     * Implementations can optimize this by resolving all secrets in a single operation
     * (e.g., one CLI call, one API request), or simply resolve each property individually.
     *
     * @param properties Map of property names to their values (which may contain secret references)
     * @return Map with all secret references resolved to their actual values
     * @throws SecretsException if the secret resolution fails
     */
    fun resolveReferences(properties: Map<String, String>): Map<String, String>

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