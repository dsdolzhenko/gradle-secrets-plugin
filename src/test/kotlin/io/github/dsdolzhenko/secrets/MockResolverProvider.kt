package io.github.dsdolzhenko.secrets

import org.gradle.api.Project

/**
 * Provider for Mock SecretsResolver used in tests.
 * Demonstrates how to implement a custom SecretsResolverProvider.
 */
class MockResolverProvider(
    private val secretsMap: Map<String, String> = emptyMap()
) : SecretsResolverProvider {

    override fun getName(): String = "MockProvider"

    override fun createResolver(project: Project, extension: SecretsExtension): SecretsResolver {
        return MockSecretsResolver(
            project.logger,
            secretsMap
        )
    }
}
