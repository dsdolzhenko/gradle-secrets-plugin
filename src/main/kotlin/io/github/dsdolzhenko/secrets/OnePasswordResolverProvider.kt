package io.github.dsdolzhenko.secrets

import org.gradle.api.Project

/**
 * Provider for 1Password SecretsResolver.
 * This provider is automatically discovered via Java ServiceLoader.
 */
class OnePasswordResolverProvider : SecretsResolverProvider {

    override fun getName(): String = "1Password"

    override fun createResolver(project: Project, extension: SecretsExtension): SecretsResolver {
        return SecretsOnePasswordResolver(
            project.logger,
            extension.cliPath.get(),
            extension.account.orNull,
            extension.cliTimeout.get()
        )
    }
}
