# Secrets Plugin

A Gradle plugin that automatically injects secrets from a secret store (only 1Password at the moment) into your build environment before task execution.

## Features

- **Automatic Secret Injection**: Replaces secret references with actual values
- **Performance**: Caches secrets during the build to minimize calls to secrets store
- **Flexible Configuration**: Control what gets injected (system properties, environment variables, project properties)
- **Task-Level Control**: Include or exclude specific tasks
- **Secure**: Secrets are only resolved when needed and can be cleared after task execution
- **Verbose Logging**: Debug mode for troubleshooting secret injection

## Prerequisites

- Gradle 7.0 or higher
- [1Password CLI](https://developer.1password.com/docs/cli) installed and configured
- Must be signed in to 1Password CLI (`op signin`)

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.dsdolzhenko.secrets") version "<version>"
}
```

## Usage

### Basic Usage

The plugin automatically detects and resolves 1Password secret references in the format:

```
op://vault-name/item-name/field-name
```

**Example: gradle.properties**
```properties
# Standard properties work as usual
app.name=MyApplication

# 1Password reference - will be resolved automatically
github.token=op://Development/GitHub-Token/credential
database.password=op://Production/DB-Credentials/password
api.key=op://Development/API-Keys/openai
```

When you run any Gradle task, the plugin will:
1. Detect these references
2. Call 1Password CLI to fetch the actual values
3. Replace the references before task execution

### Configuration

Configure the plugin in your `build.gradle.kts`:

```kotlin
onePassword {
    // Enable/disable the plugin (default: true)
    enabled.set(true)

    // Control what gets injected
    injectSystemProperties.set(true)
    injectEnvironmentVariables.set(true)
    injectProjectProperties.set(true)

    // Fail build on secret resolution errors (default: true)
    failOnError.set(true)

    // Clear secrets after each task (default: false)
    clearSecretsAfterTask.set(false)

    // 1Password CLI configuration
    cliPath.set("op") // Path to 1Password CLI
    account.set("my-account") // Optional: specific account
    cliTimeout.set(30) // Timeout in seconds

    // Exclude specific tasks
    excludeTasks("help", "tasks", "projects")

    // Only inject specific properties
    includeProperties("github.token", "api.key")

    // Or exclude specific properties
    excludeProperties("some.property")

    // Enable verbose logging
    verbose.set(false)
}
```

### Example

```kotlin
// build.gradle.kts
plugins {
    `maven-publish`
    id("io.github.dsdolzhenko.op") version "<version>"
}

publishing {
    repositories {
        maven {
            url = uri("https://maven.example.com/releases")
            credentials {
                username = project.property("maven.username") as String
                password = project.property("maven.password") as String
            }
        }
    }
}
```

**gradle.properties**
```properties
maven.username=op://CI/Maven-Repo/username
maven.password=op://CI/Maven-Repo/password
```

## 1Password Reference Format

The plugin uses the standard 1Password CLI reference format:

```
op://[vault]/[item]/[field]
```

**Examples:**
- `op://Private/GitHub/token` - GitHub token from Private vault
- `op://Production/Database/password` - Database password from Production vault
- `op://Development/API-Keys/openai` - OpenAI key from Development vault

## Security Best Practices

1. **Never commit actual secrets** - Only commit 1Password references
2. **Use appropriate vaults** - Separate development and production secrets
3. **Limit access** - Use 1Password's access controls
4. **Clear cache** - Use `clearOnePasswordCache` task when needed
5. **Fail on errors** - Keep `failOnError=true` in production
6. **Exclude tasks** - Don't inject secrets into tasks that don't need them

## Troubleshooting

### "1Password CLI command failed"

**Problem:** The plugin can't execute the 1Password CLI.

**Solutions:**
1. Ensure 1Password CLI is installed: `op --version`
2. Sign in to 1Password: `op signin`
3. Check the CLI path in configuration: `cliPath.set("/path/to/op")`

### "Invalid 1Password reference format"

**Problem:** Your secret reference doesn't match the expected format.

**Solution:** Ensure references follow: `op://vault/item/field`

### Secrets not being injected

**Problem:** Properties still contain references instead of values.

**Debug steps:**
1. Enable verbose logging: `onePassword { verbose.set(true) }`
2. Check if property is excluded: Review `excludedProperties` and `includedProperties`
3. Verify 1Password CLI access: `op read "op://vault/item/field"`

### Performance issues

**Problem:** Build is slow due to many 1Password calls.

**Solutions:**
1. Secrets are cached automatically during the build
2. Group related secrets in the same items
3. Exclude tasks that don't need secrets

## Development

### Building the plugin

```bash
cd plugin
./gradlew build
```

### Publishing locally

```bash
./gradlew publishToMavenLocal
```

### Testing

```bash
./gradlew test
```

## License

The project is licensed under the [MIT license](./LICENSE.txt).
