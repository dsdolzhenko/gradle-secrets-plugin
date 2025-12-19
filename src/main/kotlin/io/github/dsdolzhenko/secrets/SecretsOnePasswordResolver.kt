package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolves 1Password secret references by calling the 1Password CLI
 */
class SecretsOnePasswordResolver(
    private val logger: Logger,
    private val cliPath: String = "op",
    private val account: String? = null,
    private val timeout: Int = 30
) : SecretsResolver {
    
    private val secretCache = ConcurrentHashMap<String, String>()
    private val referencePattern = Pattern.compile("op://([^/]+)/([^/]+)/([^\\s\"']+)")

    /**
     * Resolves all references in a given text
     */
    override fun resolveReferences(text: String): String {
        val matcher = referencePattern.matcher(text)
        val result = StringBuffer()
        
        while (matcher.find()) {
            val reference = matcher.group(0)
            try {
                val secret = resolveSecret(reference)
                matcher.appendReplacement(result, secret)
            } catch (e: Exception) {
                logger.warn("Failed to resolve reference $reference: ${e.message}")
                throw e
            }
        }
        matcher.appendTail(result)
        
        return result.toString()
    }
    
    /**
     * Checks if a string contains any 1Password references
     */
    override fun containsReference(text: String): Boolean {
        return referencePattern.matcher(text).find()
    }
    
    /**
     * Validates 1Password reference format
     */
    fun isValidReference(reference: String): Boolean {
        return referencePattern.matcher(reference).matches()
    }
    
    /**
     * Clears the secret cache
     */
    fun clearCache() {
        secretCache.clear()
        logger.debug("Secret cache cleared")
    }

    /**
     * Resolves a secret reference to its actual value
     * Format: op://vault/item/field
     */
    private fun resolveSecret(reference: String): String {
        // Check cache first
        secretCache[reference]?.let { return it }

        logger.debug("Resolving 1Password reference: $reference")

        // Validate reference format
        if (!isValidReference(reference)) {
            throw SecretsException("Invalid 1Password reference format: $reference")
        }

        try {
            val command = buildCommand(reference, cliPath, account)
            val secret = executeCommand(command, timeout)

            // Cache the result
            secretCache[reference] = secret
            logger.debug("Successfully resolved secret for: $reference")

            return secret
        } catch (e: Exception) {
            throw SecretsException("Failed to resolve 1Password reference: $reference", e)
        }
    }
    
    private fun buildCommand(
        reference: String,
        cliPath: String,
        account: String?
    ): List<String> {
        val command = mutableListOf(cliPath, "read", reference)
        
        if (account != null) {
            command.add("--account")
            command.add(account)
        }
        
        return command
    }
    
    private fun executeCommand(command: List<String>, timeout: Int): String {
        logger.debug("Executing command: ${command.joinToString(" ")}")
        
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()
        
        val completed = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        
        if (!completed) {
            process.destroyForcibly()
            throw SecretsException("1Password CLI command timed out after $timeout seconds")
        }
        
        val exitCode = process.exitValue()
        
        if (exitCode != 0) {
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream))
                .readText()
                .trim()
            throw SecretsException(
                "1Password CLI command failed with exit code $exitCode: $errorOutput"
            )
        }
        
        return BufferedReader(InputStreamReader(process.inputStream))
            .readText()
            .trim()
    }
}