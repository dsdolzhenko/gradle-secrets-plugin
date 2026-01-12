package io.github.dsdolzhenko.secrets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MockOnePasswordCommandExecutorTest {

    private lateinit var executor: MockOnePasswordCommandExecutor

    @BeforeEach
    fun setup() {
        executor = MockOnePasswordCommandExecutor()
    }

    @Test
    fun `inject should resolve secrets from template`() {
        // Given
        executor.addSecret("op://vault/github/token", "ghp_test_token")
        executor.addSecret("op://vault/api/key", "sk_test_key")

        val template = """{"github.token":"op://vault/github/token","api.key":"op://vault/api/key"}"""

        // When
        val result = executor.inject(template, null, 30)

        // Then
        val resultJson = Json.parseToJsonElement(result) as JsonObject
        assertEquals("ghp_test_token", resultJson["github.token"]?.jsonPrimitive?.content)
        assertEquals("sk_test_key", resultJson["api.key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `inject should record call parameters`() {
        // Given
        executor.addSecret("op://vault/test/secret", "test_value")
        val template = """{"test":"op://vault/test/secret"}"""

        // When
        executor.inject(template, "my-account", 60)

        // Then
        assertEquals(1, executor.injectCalls.size)
        val call = executor.injectCalls[0]
        assertEquals(template, call.template)
        assertEquals("my-account", call.account)
        assertEquals(60, call.timeout)
    }

    @Test
    fun `inject should throw exception for unknown secret`() {
        // Given
        val template = """{"test":"op://vault/unknown/secret"}"""

        // When/Then
        val exception = assertThrows<SecretsException> {
            executor.inject(template, null, 30)
        }
        assertTrue(exception.message?.contains("Unknown secret reference") == true)
    }

    @Test
    fun `inject should handle non-secret values in template`() {
        // Given
        executor.addSecret("op://vault/test/secret", "secret_value")
        val template = """{"secret":"op://vault/test/secret","plain":"plain_value"}"""

        // When
        val result = executor.inject(template, null, 30)

        // Then
        val resultJson = Json.parseToJsonElement(result) as JsonObject
        assertEquals("secret_value", resultJson["secret"]?.jsonPrimitive?.content)
        assertEquals("plain_value", resultJson["plain"]?.jsonPrimitive?.content)
    }

    @Test
    fun `addSecrets should add multiple secrets at once`() {
        // Given
        val secrets = mapOf(
            "op://vault/test/secret1" to "value1",
            "op://vault/test/secret2" to "value2"
        )

        // When
        executor.addSecrets(secrets)

        // Then - verify by injecting
        val result = executor.inject("""{"s1":"op://vault/test/secret1","s2":"op://vault/test/secret2"}""", null, 30)
        val resultJson = Json.parseToJsonElement(result) as JsonObject
        assertEquals("value1", resultJson["s1"]?.jsonPrimitive?.content)
        assertEquals("value2", resultJson["s2"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clearCalls should clear all recorded calls`() {
        // Given
        executor.addSecret("op://vault/test/secret", "test_value")
        executor.inject("""{"test":"op://vault/test/secret"}""", null, 30)

        // When
        executor.clearCalls()

        // Then
        assertEquals(0, executor.injectCalls.size)
    }

    @Test
    fun `clearSecrets should clear all mock secrets`() {
        // Given
        executor.addSecret("op://vault/test/secret", "test_value")

        // When
        executor.clearSecrets()

        // Then
        assertThrows<SecretsException> {
            executor.inject("""{"test":"op://vault/test/secret"}""", null, 30)
        }
    }
}
