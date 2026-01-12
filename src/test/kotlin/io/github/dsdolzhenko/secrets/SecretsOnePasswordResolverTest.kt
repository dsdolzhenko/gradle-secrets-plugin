package io.github.dsdolzhenko.secrets

import org.gradle.api.logging.Logger
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecretsOnePasswordResolverTest {

    private lateinit var logger: Logger
    private lateinit var mockExecutor: MockOnePasswordCommandExecutor
    private lateinit var resolver: SecretsOnePasswordResolver

    @BeforeEach
    fun setup() {
        val project = ProjectBuilder.builder().build()
        logger = project.logger
        mockExecutor = MockOnePasswordCommandExecutor()
        resolver = SecretsOnePasswordResolver(
            logger = logger,
            commandExecutor = mockExecutor,
            account = null,
            timeout = 30
        )
    }

    @Test
    fun `getName should return 1Password`() {
        assertEquals("1Password", resolver.getName())
    }

    @Test
    fun `containsReference should detect op references`() {
        assertTrue(resolver.containsReference("op://vault/item/field"))
        assertTrue(resolver.containsReference("Some text op://vault/item/field more text"))
        assertFalse(resolver.containsReference("No secrets here"))
    }

    @Test
    fun `resolveReferences should resolve all secrets in one CLI call`() {
        // Given
        mockExecutor.addSecret("op://vault/github/token", "ghp_test_token")
        mockExecutor.addSecret("op://vault/api/key", "sk_test_key")

        val properties = mapOf(
            "github.token" to "op://vault/github/token",
            "api.key" to "op://vault/api/key",
            "plain.value" to "no_secret_here"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals("ghp_test_token", resolved["github.token"])
        assertEquals("sk_test_key", resolved["api.key"])
        assertEquals("no_secret_here", resolved["plain.value"])

        // Verify only ONE inject call was made (batch resolution)
        assertEquals(1, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should handle properties without secrets`() {
        // Given
        val properties = mapOf(
            "plain.value1" to "no_secret_here",
            "plain.value2" to "also_no_secret"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals(properties, resolved)

        // Verify NO CLI calls were made
        assertEquals(0, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should handle mixed properties with and without secrets`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret", "secret_value")

        val properties = mapOf(
            "with.secret" to "op://vault/test/secret",
            "without.secret" to "plain_value"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals("secret_value", resolved["with.secret"])
        assertEquals("plain_value", resolved["without.secret"])
    }

    @Test
    fun `resolveReferences should handle properties with multiple references in one value`() {
        // Given
        mockExecutor.addSecret("op://vault/user/name", "john")
        mockExecutor.addSecret("op://vault/user/pass", "secret123")

        val properties = mapOf(
            "connection" to "user=op://vault/user/name;password=op://vault/user/pass"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals("user=john;password=secret123", resolved["connection"])
    }

    @Test
    fun `resolveReferences should use cache and skip CLI call for cached secrets`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret", "secret_value")

        // First call to populate cache
        val properties1 = mapOf("key1" to "op://vault/test/secret")
        resolver.resolveReferences(properties1)

        // Clear mock calls to verify next call doesn't make CLI calls
        mockExecutor.clearCalls()

        // When - second call with same secret
        val properties2 = mapOf("key2" to "op://vault/test/secret")
        val resolved = resolver.resolveReferences(properties2)

        // Then
        assertEquals("secret_value", resolved["key2"])

        // Verify NO new CLI calls were made (used cache)
        assertEquals(0, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should make CLI call only for uncached secrets`() {
        // Given
        mockExecutor.addSecret("op://vault/cached/secret", "cached_value")
        mockExecutor.addSecret("op://vault/new/secret", "new_value")

        // First call to populate cache with one secret
        resolver.resolveReferences(mapOf("key1" to "op://vault/cached/secret"))

        mockExecutor.clearCalls()

        // When - second call with one cached and one new secret
        val properties = mapOf(
            "cached.key" to "op://vault/cached/secret",
            "new.key" to "op://vault/new/secret"
        )
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals("cached_value", resolved["cached.key"])
        assertEquals("new_value", resolved["new.key"])

        // Verify only ONE inject call was made for the new secret
        assertEquals(1, mockExecutor.injectCalls.size)

        // Verify the inject call only contained the new secret
        val template = mockExecutor.injectCalls[0].template
        assertTrue(template.contains("op://vault/new/secret"))
        assertFalse(template.contains("op://vault/cached/secret"))
    }

    @Test
    fun `resolveReferences should throw exception when secret resolution fails`() {
        // Given - no secrets added to mock, so resolution will fail
        val properties = mapOf("key" to "op://vault/unknown/secret")

        // When/Then
        assertThrows<SecretsException> {
            resolver.resolveReferences(properties)
        }
    }

    @Test
    fun `clearCache should clear the secret cache`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret", "secret_value")

        // Populate cache
        resolver.resolveReferences(mapOf("key" to "op://vault/test/secret"))
        mockExecutor.clearCalls()

        // When
        resolver.clearCache()

        // Then - next call should make CLI call again
        resolver.resolveReferences(mapOf("key" to "op://vault/test/secret"))
        assertEquals(1, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should work with single property containing references`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret", "secret_value")

        // When
        val result = resolver.resolveReferences(mapOf("prop" to "My secret is op://vault/test/secret here"))

        // Then
        assertEquals("My secret is secret_value here", result["prop"])
    }

    @Test
    fun `resolveReferences should handle properties with no references`() {
        // Given
        val properties = mapOf("prop" to "No secrets here")

        // When
        val result = resolver.resolveReferences(properties)

        // Then
        assertEquals("No secrets here", result["prop"])
    }

    @Test
    fun `resolveReferences should resolve multiple secrets in one property`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret1", "value1")
        mockExecutor.addSecret("op://vault/test/secret2", "value2")

        // When
        val result = resolver.resolveReferences(mapOf("prop" to "s1=op://vault/test/secret1 s2=op://vault/test/secret2"))

        // Then
        assertEquals("s1=value1 s2=value2", result["prop"])

        // Verify it used inject (batch resolution)
        assertEquals(1, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should handle empty properties map`() {
        // Given
        val properties = emptyMap<String, String>()

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertTrue(resolved.isEmpty())
        assertEquals(0, mockExecutor.injectCalls.size)
    }

    @Test
    fun `resolveReferences should resolve multiple properties with same reference in one batch`() {
        // Given
        mockExecutor.addSecret("op://vault/test/secret", "secret_value")

        val properties = mapOf(
            "key1" to "op://vault/test/secret",
            "key2" to "op://vault/test/secret",
            "key3" to "prefix_op://vault/test/secret"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals("secret_value", resolved["key1"])
        assertEquals("secret_value", resolved["key2"])
        assertEquals("prefix_secret_value", resolved["key3"])

        // Verify only ONE inject call was made (all properties resolved in one batch)
        assertEquals(1, mockExecutor.injectCalls.size)

        // Verify the template is valid JSON with all three properties
        val template = mockExecutor.injectCalls[0].template
        assertTrue(template.contains("\"key1\""))
        assertTrue(template.contains("\"key2\""))
        assertTrue(template.contains("\"key3\""))
    }

    @Test
    fun `resolveReferences should handle multi-line secret values`() {
        // Given
        val multiLineValue = "line1\nline2\nline3"
        mockExecutor.addSecret("op://vault/test/multiline", multiLineValue)

        val properties = mapOf(
            "ssh.key" to "op://vault/test/multiline"
        )

        // When
        val resolved = resolver.resolveReferences(properties)

        // Then
        assertEquals(multiLineValue, resolved["ssh.key"])

        // Verify the inject call was made
        assertEquals(1, mockExecutor.injectCalls.size)
    }
}
