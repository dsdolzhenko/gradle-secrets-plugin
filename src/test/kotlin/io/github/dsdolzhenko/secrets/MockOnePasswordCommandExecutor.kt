package io.github.dsdolzhenko.secrets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.util.regex.Pattern

/**
 * Mock implementation of OnePasswordCommandExecutor for testing.
 * This allows testing without requiring the actual op CLI.
 */
class MockOnePasswordCommandExecutor : OnePasswordCommandExecutor {
    private val secretsMap = mutableMapOf<String, String>()
    private val _injectCalls = mutableListOf<InjectCall>()
    private val referencePattern = Pattern.compile("op://([^/]+)/([^/]+)/([a-zA-Z0-9_./\\-]+)")

    /**
     * Records all inject() calls for verification in tests
     */
    val injectCalls: List<InjectCall>
        get() = _injectCalls.toList()

    /**
     * Adds a mock secret that will be returned when resolving references
     */
    fun addSecret(reference: String, value: String) {
        secretsMap[reference] = value
    }

    /**
     * Adds multiple mock secrets at once
     */
    fun addSecrets(secrets: Map<String, String>) {
        secretsMap.putAll(secrets)
    }

    /**
     * Clears all recorded calls
     */
    fun clearCalls() {
        _injectCalls.clear()
    }

    /**
     * Clears all mock secrets
     */
    fun clearSecrets() {
        secretsMap.clear()
    }

    override fun inject(template: String, account: String?, timeout: Int): String {
        _injectCalls.add(InjectCall(template, account, timeout))

        try {
            // Parse the JSON template
            val jsonObject = Json.parseToJsonElement(template) as? JsonObject
                ?: throw SecretsException("Template is not a valid JSON object")

            // Resolve each property's references
            val resolvedMap = jsonObject.mapValues { (_, value) ->
                resolveReferencesInText(value.jsonPrimitive.content)
            }

            // Return as JSON
            return Json.encodeToString(
                JsonObject.serializer(),
                JsonObject(resolvedMap.mapValues { JsonPrimitive(it.value) })
            )
        } catch (e: Exception) {
            if (e is SecretsException) throw e
            throw SecretsException("Failed to parse or resolve template: ${e.message}", e)
        }
    }

    /**
     * Resolves all secret references in the given text
     */
    private fun resolveReferencesInText(text: String): String {
        var result = text
        val matcher = referencePattern.matcher(text)
        val references = mutableListOf<String>()

        while (matcher.find()) {
            references.add(matcher.group(0))
        }

        references.forEach { reference ->
            val value = secretsMap[reference]
                ?: throw SecretsException("Unknown secret reference: $reference")
            result = result.replace(reference, value)
        }

        return result
    }

    /**
     * Data class to record inject() call parameters
     */
    data class InjectCall(
        val template: String,
        val account: String?,
        val timeout: Int
    )
}
