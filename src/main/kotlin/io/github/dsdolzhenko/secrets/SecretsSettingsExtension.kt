package io.github.dsdolzhenko.secrets

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension for configuring the Secrets settings plugin.
 *
 * Configure `cliPath`/`account`/`cliTimeout` before any `dotenv.get(...)`/`dotenv["..."]`
 * call that should be resolved, since the 1Password resolver is built lazily from whatever
 * configuration is in place at that point.
 */
abstract class SecretsSettingsExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Path to 1Password CLI executable. Default: "op"
     */
    val cliPath: Property<String> = objects.property(String::class.java)
        .convention("op")

    /**
     * 1Password account identifier (optional)
     * If not set, uses the currently signed-in account
     */
    val account: Property<String> = objects.property(String::class.java)

    /**
     * Timeout for 1Password CLI calls in seconds. Default: 30
     */
    val cliTimeout: Property<Int> = objects.property(Int::class.java)
        .convention(30)

    /**
     * Fail the build if secret resolution fails. Default: true
     */
    val failOnError: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Enable verbose logging for debugging. Default: false
     */
    val verbose: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)
}
