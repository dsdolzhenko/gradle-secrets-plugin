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
}