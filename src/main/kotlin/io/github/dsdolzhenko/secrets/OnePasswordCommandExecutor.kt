package io.github.dsdolzhenko.secrets

/**
 * Interface for executing 1Password CLI commands.
 * This abstraction enables testing without requiring the actual op CLI.
 */
interface OnePasswordCommandExecutor {
    /**
     * Executes op inject with the given template content.
     *
     * The template should contain secret references in the 1Password format (op://vault/item/field).
     * These references will be replaced with actual secret values.
     *
     * @param template The template string containing secret references
     * @param account Optional account identifier for 1Password CLI
     * @param timeout Timeout in seconds for the CLI command
     * @return The resolved template with secrets injected
     * @throws SecretsException if the command fails or times out
     */
    fun inject(template: String, account: String?, timeout: Int): String
}
