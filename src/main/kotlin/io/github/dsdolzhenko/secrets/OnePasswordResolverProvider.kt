package io.github.dsdolzhenko.secrets

import org.gradle.api.Project

/**
 * Provider for 1Password SecretsResolver.
 * This provider is automatically discovered via Java ServiceLoader.
 */
class OnePasswordResolverProvider : SecretsResolverProvider {

    override fun getName(): String = "1Password"

    override fun createResolver(project: Project, extension: SecretsExtension): SecretsResolver {
        val commandExecutor = ProcessOnePasswordCommandExecutor(
            project.logger,
            extension.cliPath.get()
        )

        return SecretsOnePasswordResolver(
            logger = project.logger,
            commandExecutor = commandExecutor,
            account = extension.account.orNull,
            timeout = extension.cliTimeout.get()
        )
    }
}
