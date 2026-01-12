package io.github.dsdolzhenko.secrets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Resolves 1Password secret references by calling the 1Password CLI
 */
class SecretsOnePasswordResolver(
    private val logger: Logger,
    private val commandExecutor: OnePasswordCommandExecutor,
    private val account: String? = null,
    private val timeout: Int = 30
) : SecretsResolver {

    override fun getName(): String = "1Password"

    private val secretCache = ConcurrentHashMap<String, String>()

    // Pattern matches: op://vault/item/field where the field contains alphanumeric, underscore, dot, hyphen, or slash
    // Stops at whitespace, quotes, or other special characters like semicolon
    private val referencePattern = Pattern.compile("op://([^/]+)/([^/]+)/([a-zA-Z0-9_./\\-]+)")

    /**
     * Resolves all references in a batch of properties.
     * This minimizes CLI invocations by resolving all secrets in one op inject call.
     *
     * @param properties Map of property names to their values (which may contain secret references)
     * @return Map with all secret references resolved
     */
    override fun resolveReferences(properties: Map<String, String>): Map<String, String> {
        if (properties.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, String>()

        // Separate properties with and without references
        val propertiesWithReferences = mutableMapOf<String, String>()

        properties.forEach { (key, value) ->
            if (containsReference(value)) {
                propertiesWithReferences[key] = value
            } else {
                result[key] = value
            }
        }

        if (propertiesWithReferences.isEmpty()) {
            return properties
        }

        // Check which properties can be resolved from the cache
        val propertiesToResolve = mutableMapOf<String, String>()
        val cachedProperties = mutableMapOf<String, String>()

        propertiesWithReferences.forEach { (key, value) ->
            val references = collectReferences(value)
            if (references.all { secretCache.containsKey(it) }) {
                cachedProperties[key] = value
            } else {
                propertiesToResolve[key] = value
            }
        }

        // Resolve properties using cache
        cachedProperties.forEach { (key, value) ->
            result[key] = replaceReferencesInText(value, collectReferences(value))
        }

        // Resolve remaining properties via CLI
        if (propertiesToResolve.isNotEmpty()) {
            logger.debug("Resolving ${propertiesToResolve.size} properties with uncached secrets in batch")

            // Create a simple properties template for op inject
            val template = createTemplate(propertiesToResolve)

            // Execute op inject
            val resolvedOutput = commandExecutor.inject(template, account, timeout)

            // Parse results
            val resolvedMap = parsePropertiesOutput(resolvedOutput)

            // Add resolved properties to result and update cache where possible
            propertiesToResolve.forEach { (key, value) ->
                val resolvedValue = resolvedMap[key]
                    ?: throw SecretsException("Failed to resolve property: $key")

                result[key] = resolvedValue

                // Only cache when the property value is exactly one reference
                val references = collectReferences(value)
                if (references.size == 1 && value == references[0]) {
                    secretCache[references[0]] = resolvedValue
                    logger.debug("Cached secret for: ${references[0]}")
                }
            }
        }

        return result
    }

    /**
     * Checks if a string contains any 1Password references
     */
    override fun containsReference(text: String): Boolean {
        return referencePattern.matcher(text).find()
    }

    /**
     * Clears the secret cache
     */
    override fun clearCache() {
        secretCache.clear()
        logger.debug("Secret cache cleared")
    }

    /**
     * Collects all unique secret references from a text
     */
    private fun collectReferences(text: String): List<String> {
        val references = mutableListOf<String>()
        val matcher = referencePattern.matcher(text)

        while (matcher.find()) {
            references.add(matcher.group(0))
        }

        return references.distinct()
    }

    /**
     * Creates a template for op inject
     * Uses original property keys to be able to map the properties back
     */
    private fun createTemplate(properties: Map<String, String>): String {
        return Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(properties.mapValues { JsonPrimitive(it.value) })
        )
    }

    /**
     * Parses the output from `op inject`
     */
    private fun parsePropertiesOutput(output: String): Map<String, String> {
        try {
            val jsonObject = Json.parseToJsonElement(output) as JsonObject
            return jsonObject.mapValues { it.value.jsonPrimitive.content }
        } catch (e: Exception) {
            throw SecretsException("Failed to parse JSON result from op inject: ${e.message}", e)
        }
    }

    /**
     * Replaces references in text with their resolved values from the cache
     */
    private fun replaceReferencesInText(text: String, references: List<String>): String {
        var result = text

        references.forEach { reference ->
            val value = secretCache[reference]
                ?: throw SecretsException("Secret not found in cache: $reference")
            result = result.replace(reference, value)
        }

        return result
    }
}