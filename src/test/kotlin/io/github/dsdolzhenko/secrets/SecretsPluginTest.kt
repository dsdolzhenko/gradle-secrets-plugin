package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SecretsPlugin
 */
class SecretsPluginTest {

    @Test
    fun `plugin applies successfully`() {
        // Given
        val project = ProjectBuilder.builder().build()

        // When
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")

        // Then
        assertTrue(project.plugins.hasPlugin("io.github.dsdolzhenko.secrets"))
    }

    @Test
    fun `plugin creates extension`() {
        // Given
        val project = ProjectBuilder.builder().build()

        // When
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")

        // Then
        val extension = project.extensions.findByName("secrets")
        assertNotNull(extension)
        assertTrue(extension is SecretsExtension)
    }

    @Test
    fun `extension has default values`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")

        // When
        val extension = project.extensions.getByType(SecretsExtension::class.java)

        // Then
        assertTrue(extension.enabled.get())
        assertTrue(extension.injectSystemProperties.get())
        assertTrue(extension.injectEnvironmentVariables.get())
        assertTrue(extension.injectProjectProperties.get())
        assertTrue(extension.failOnError.get())
        assertEquals("op", extension.cliPath.get())
        assertEquals(30, extension.cliTimeout.get())
    }

    @Test
    fun `extension can be configured`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")

        // When
        val extension = project.extensions.getByType(SecretsExtension::class.java)
        extension.enabled.set(false)
        extension.cliPath.set("/custom/path/to/op")
        extension.account.set("my-account")
        extension.cliTimeout.set(60)
        extension.verbose.set(true)

        // Then
        assertEquals(false, extension.enabled.get())
        assertEquals("/custom/path/to/op", extension.cliPath.get())
        assertEquals("my-account", extension.account.get())
        assertEquals(60, extension.cliTimeout.get())
        assertEquals(true, extension.verbose.get())
    }

    @Test
    fun `clearSecretsCache task is registered`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")

        // When
        // Trigger afterEvaluate
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        // Then
        val task = project.tasks.findByName("clearSecretsCache")
        assertNotNull(task)
        assertEquals("secrets", task.group)
        assertEquals("Clears cached secrets from the resolver", task.description)
    }

    @Test
    fun `extension convenience methods work`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")
        val extension = project.extensions.getByType(SecretsExtension::class.java)

        // When
        extension.excludeTasks("task1", "task2")
        extension.includeProperties("prop1", "prop2")
        extension.excludeProperties("prop3", "prop4")

        // Then
        assertTrue(extension.excludedTasks.get().contains("task1"))
        assertTrue(extension.excludedTasks.get().contains("task2"))
        assertTrue(extension.includedProperties.get().contains("prop1"))
        assertTrue(extension.includedProperties.get().contains("prop2"))
        assertTrue(extension.excludedProperties.get().contains("prop3"))
        assertTrue(extension.excludedProperties.get().contains("prop4"))
    }

    @Test
    fun `mock resolver can resolve references`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val mockSecrets = mapOf(
            "api-key" to "secret-value-123",
            "db-password" to "super-secret-pwd"
        )
        val resolver = MockSecretsResolver(project.logger, mockSecrets)

        // When
        val properties = mapOf("config" to "API_KEY=mock://api-key and DB_PASSWORD=mock://db-password")
        val result = resolver.resolveReferences(properties)

        // Then
        assertEquals("API_KEY=secret-value-123 and DB_PASSWORD=super-secret-pwd", result["config"])
        assertEquals(2, resolver.getResolvedSecrets().size)
        assertTrue(resolver.getResolvedSecrets().contains("api-key"))
        assertTrue(resolver.getResolvedSecrets().contains("db-password"))
    }

    @Test
    fun `mock resolver detects references`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val resolver = MockSecretsResolver(project.logger, emptyMap())

        // When & Then
        assertTrue(resolver.containsReference("mock://some-secret"))
        assertTrue(resolver.containsReference("prefix mock://secret suffix"))
        assertTrue(!resolver.containsReference("no reference here"))
    }

    @Test
    fun `mock resolver clearCache works`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val mockSecrets = mapOf("key" to "value")
        val resolver = MockSecretsResolver(project.logger, mockSecrets)

        // Resolve some secrets first
        resolver.resolveReferences(mapOf("prop" to "mock://key"))

        // When
        resolver.clearCache()

        // Then
        assertTrue(resolver.wasCacheCleared())
        assertEquals(0, resolver.getResolvedSecrets().size)
    }

    @Test
    fun `composite resolver chains multiple resolvers`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val resolver1 = MockSecretsResolver(
            project.logger,
            mapOf("key1" to "value1"),
            "mock1://"
        )
        val resolver2 = MockSecretsResolver(
            project.logger,
            mapOf("key2" to "value2"),
            "mock2://"
        )
        val composite = CompositeSecretsResolver(project.logger, listOf(resolver1, resolver2))

        // When
        val properties = mapOf("config" to "First: mock1://key1, Second: mock2://key2")
        val result = composite.resolveReferences(properties)

        // Then
        assertEquals("First: value1, Second: value2", result["config"])
        assertEquals(1, resolver1.getResolvedSecrets().size)
        assertEquals(1, resolver2.getResolvedSecrets().size)
    }

    @Test
    fun `composite resolver detects references from any resolver`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val resolver1 = MockSecretsResolver(project.logger, emptyMap(), "mock1://")
        val resolver2 = MockSecretsResolver(project.logger, emptyMap(), "mock2://")
        val composite = CompositeSecretsResolver(project.logger, listOf(resolver1, resolver2))

        // When & Then
        assertTrue(composite.containsReference("mock1://secret"))
        assertTrue(composite.containsReference("mock2://secret"))
        assertTrue(!composite.containsReference("no reference"))
    }

    @Test
    fun `composite resolver clears all caches`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val resolver1 = MockSecretsResolver(project.logger, mapOf("k1" to "v1"), "mock1://")
        val resolver2 = MockSecretsResolver(project.logger, mapOf("k2" to "v2"), "mock2://")
        val composite = CompositeSecretsResolver(project.logger, listOf(resolver1, resolver2))

        // Resolve some secrets
        composite.resolveReferences(mapOf("prop" to "mock1://k1 mock2://k2"))

        // When
        composite.clearCache()

        // Then
        assertTrue(resolver1.wasCacheCleared())
        assertTrue(resolver2.wasCacheCleared())
    }

    @Test
    fun `resolver has correct getName`() {
        // Given
        val project = ProjectBuilder.builder().build()
        val mockResolver = MockSecretsResolver(project.logger)
        val mockExecutor = MockOnePasswordCommandExecutor()
        val opResolver = SecretsOnePasswordResolver(
            logger = project.logger,
            commandExecutor = mockExecutor
        )

        // When & Then
        assertEquals("Mock", mockResolver.getName())
        assertEquals("1Password", opResolver.getName())
    }

    @Test
    fun `provider can create resolver`() {
        // Given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.dsdolzhenko.secrets")
        val extension = project.extensions.getByType(SecretsExtension::class.java)
        val provider = OnePasswordResolverProvider()

        // When
        val resolver = provider.createResolver(project, extension)

        // Then
        assertNotNull(resolver)
        assertEquals("1Password", resolver.getName())
    }

    @Test
    fun `mock provider has correct properties`() {
        // Given
        val provider = MockResolverProvider()

        // When & Then
        assertEquals("MockProvider", provider.getName())
    }
}
