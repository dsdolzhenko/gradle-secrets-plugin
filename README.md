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

## Usage

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("io.github.dsdolzhenko.secrets") version "<version>"
}
```

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
github.token="op://Development/GitHub/credential"
database.password="op://Development/Database/password"
api.key="op://Development/API/password"
```

When you run any Gradle task, the plugin will:
1. Detect these references
2. Call 1Password CLI to fetch the actual values
3. Replace the references before task execution

### Configuration

Configure the plugin in your `build.gradle.kts`:

```kotlin
secrets {
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
