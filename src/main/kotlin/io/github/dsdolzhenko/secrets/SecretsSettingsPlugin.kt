package io.github.dsdolzhenko.secrets

import io.github.dsdolzhenko.dotenv.DotEnvSettingsExtension
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging

/**
 * Gradle settings plugin that resolves 1Password secret references (`op://...`) in values
 * read through the companion `io.github.dsdolzhenko.dotenv.settings` plugin's `dotenv`
 * extension, so a `.env` entry such as `SPACE_TOKEN=op://vault/item/field` resolves to the
 * real secret everywhere `dotenv.get("SPACE_TOKEN")`/`dotenv["SPACE_TOKEN"]` is called, both
 * in `settings.gradle.kts` and in every project's `build.gradle.kts`.
 */
class SecretsSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val extension = settings.extensions.create("secrets", SecretsSettingsExtension::class.java)

        settings.pluginManager.apply("io.github.dsdolzhenko.dotenv.settings")
        val dotenv = settings.extensions.getByType(DotEnvSettingsExtension::class.java)

        val logger = Logging.getLogger(SecretsSettingsPlugin::class.java)

        // Built lazily, on first secret-bearing value, so it picks up whatever `secrets { ... }`
        // configuration is in place by then (mirroring DotEnvSettingsExtension's own laziness).
        val resolver by lazy {
            SecretsOnePasswordResolver(
                logger = Logging.getLogger(SecretsOnePasswordResolver::class.java),
                commandExecutor = ProcessOnePasswordCommandExecutor(logger, extension.cliPath.get(), settings.providers),
                account = extension.account.orNull,
                timeout = extension.cliTimeout.get()
            )
        }

        dotenv.resolveValuesWith { value ->
            if (!resolver.containsReference(value)) {
                value
            } else {
                try {
                    val resolved = resolver.resolveReferences(mapOf("value" to value))["value"] ?: value
                    if (extension.verbose.get()) {
                        logger.lifecycle("Resolved secret reference in a dotenv value")
                    }
                    resolved
                } catch (e: Exception) {
                    if (extension.failOnError.get()) {
                        throw SecretsException("Failed to resolve secret reference in a dotenv value", e)
                    } else {
                        logger.warn("Failed to resolve secret reference in a dotenv value: ${e.message}")
                        value
                    }
                }
            }
        }
    }
}
