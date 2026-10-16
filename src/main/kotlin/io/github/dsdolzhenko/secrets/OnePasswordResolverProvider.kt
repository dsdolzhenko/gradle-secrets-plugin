package io.github.dsdolzhenko.secrets

import org.gradle.api.Project
import org.gradle.api.logging.Logging

/**
 * Provider for 1Password SecretsResolver.
 * This provider is automatically discovered via Java ServiceLoader.
 */
class OnePasswordResolverProvider : SecretsResolverProvider {

    override fun getName(): String = "1Password"

    override fun createResolver(project: Project, extension: SecretsExtension): SecretsResolver {
        // Use a plain, class-scoped logger rather than project.logger: the resolver is held
        // by task actions, which must not reach back into Project under the configuration cache.
        val logger = Logging.getLogger(SecretsOnePasswordResolver::class.java)
        val commandExecutor = ProcessOnePasswordCommandExecutor(
            logger,
            extension.cliPath.get(),
            project.providers
        )

        return SecretsOnePasswordResolver(
            logger = logger,
            commandExecutor = commandExecutor,
            account = extension.account.orNull,
            timeout = extension.cliTimeout.get()
        )
    }
}
