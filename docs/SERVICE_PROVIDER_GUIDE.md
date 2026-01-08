# Service Provider Interface (SPI) Guide

## Overview

The Secrets Plugin uses Java's ServiceLoader mechanism to automatically discover and load secret resolver implementations. This allows multiple secret providers to coexist and be discovered automatically without manual configuration.

## Architecture

The plugin uses a provider pattern with three main components:

1. **SecretsResolver** - Interface for resolving secret references
2. **SecretsResolverProvider** - SPI interface for creating resolver instances
3. **ServiceLoader** - JVM mechanism for automatic discovery

## How It Works

### 1. Automatic Discovery

When the plugin initializes, it:

1. Uses `ServiceLoader` to discover all `SecretsResolverProvider` implementations on the classpath
2. Creates resolver instances from each provider
3. Chains them together in a `CompositeSecretsResolver` if multiple resolvers exist

### 2. Provider Registration

Providers are registered via the standard Java SPI mechanism:

Create a file: `src/main/resources/META-INF/services/io.github.dsdolzhenko.secrets.SecretsResolverProvider`

Content: Fully qualified class names of your provider implementations (one per line)

```
io.github.dsdolzhenko.secrets.OnePasswordResolverProvider
com.example.MyCustomResolverProvider
```

## Creating a Custom Resolver

### Step 1: Implement SecretsResolver

```kotlin
package com.example

import io.github.dsdolzhenko.secrets.SecretsResolver
import org.gradle.api.logging.Logger

class MyCustomResolver(
    private val logger: Logger,
    private val config: String
) : SecretsResolver {

    override fun getName(): String = "MyCustom"

    override fun resolveReferences(text: String): String {
        // Your resolution logic here
        return text.replace("custom://", "resolved-value-")
    }

    override fun containsReference(text: String): Boolean {
        return text.contains("custom://")
    }

    override fun clearCache() {
        // Clear any cached secrets
    }
}
```

### Step 2: Implement SecretsResolverProvider

```kotlin
package com.example

import io.github.dsdolzhenko.secrets.SecretsResolverProvider
import io.github.dsdolzhenko.secrets.SecretsResolver
import io.github.dsdolzhenko.secrets.SecretsExtension
import org.gradle.api.Project

class MyCustomResolverProvider : SecretsResolverProvider {

    override fun getName(): String = "MyCustomProvider"

    override fun createResolver(
        project: Project,
        extension: SecretsExtension
    ): SecretsResolver? {
        // Only create if appropriate (e.g., if config is present)
        return MyCustomResolver(project.logger, "config-value")
    }
}
```

### Step 3: Register via SPI

Create file: `src/main/resources/META-INF/services/io.github.dsdolzhenko.secrets.SecretsResolverProvider`

```
com.example.MyCustomResolverProvider
```

### Step 4: Add to Classpath

Add your custom resolver as a dependency in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.example:my-custom-resolver:1.0.0")
}
```

## Multiple Resolvers

When multiple resolvers are active:

1. Text is processed by each resolver in discovery order
2. Each resolver only processes its own reference format
3. Multiple reference formats can coexist in the same text

Example:
```
API_KEY=op://vault/item/field
DB_PASSWORD=custom://db/password
```

Both `op://` (1Password) and `custom://` references will be resolved by their respective resolvers.

## Testing with Custom Resolvers

Register a test provider via SPI in `src/test/resources/META-INF/services/io.github.dsdolzhenko.secrets.SecretsResolverProvider`.

## Built-in Resolvers

### 1Password Resolver

- **Provider**: `OnePasswordResolverProvider`
- **Format**: `op://vault/item/field`
- **Requirements**: 1Password CLI (`op`) must be installed

## Extension Configuration

The plugin respects extension configuration for all resolvers:

```kotlin
secrets {
    enabled.set(true)
    verbose.set(true)
    cliPath.set("op")  // 1Password specific
    account.set("my-account")  // 1Password specific
    cliTimeout.set(30)  // 1Password specific
}
```

Custom resolvers can access the extension in their provider's `createResolver` method.

## Debugging

Enable verbose logging to see resolver discovery and usage:

```kotlin
secrets {
    verbose.set(true)
}
```

This will log:
- Discovered providers
- Created resolvers
- Resolution attempts

## Example: AWS Secrets Manager Resolver

Here's an example of creating an AWS Secrets Manager resolver:

```kotlin
// AwsSecretsResolver.kt
class AwsSecretsResolver(
    private val logger: Logger,
    private val region: String
) : SecretsResolver {

    private val client = SecretsManagerClient.builder()
        .region(Region.of(region))
        .build()

    override fun getName(): String = "AWS Secrets Manager"

    override fun resolveReferences(text: String): String {
        val pattern = Pattern.compile("aws://([^\\s]+)")
        val matcher = pattern.matcher(text)
        val result = StringBuffer()

        while (matcher.find()) {
            val secretName = matcher.group(1)
            val secret = getSecret(secretName)
            matcher.appendReplacement(result, secret)
        }
        matcher.appendTail(result)
        return result.toString()
    }

    override fun containsReference(text: String): Boolean {
        return text.contains("aws://")
    }

    private fun getSecret(secretName: String): String {
        val request = GetSecretValueRequest.builder()
            .secretId(secretName)
            .build()
        return client.getSecretValue(request).secretString()
    }
}

// AwsSecretsResolverProvider.kt
class AwsSecretsResolverProvider : SecretsResolverProvider {
    override fun getName(): String = "AWS"

    override fun createResolver(
        project: Project,
        extension: SecretsExtension
    ): SecretsResolver? {
        val region = System.getenv("AWS_REGION") ?: "us-east-1"
        return AwsSecretsResolver(project.logger, region)
    }
}
```

Register in `META-INF/services/io.github.dsdolzhenko.secrets.SecretsResolverProvider`:
```
com.example.aws.AwsSecretsResolverProvider
```

Now you can use both 1Password and AWS Secrets Manager in the same project:
```
DATABASE_URL=aws://prod/database/url
API_KEY=op://vault/api/key
```

## Benefits of SPI Approach

1. **Automatic Discovery** - No manual configuration needed
2. **Multiple Providers** - Support multiple secret backends simultaneously
3. **Extensibility** - Easy to add new providers without modifying the plugin
4. **Loose Coupling** - Providers don't need to know about each other
5. **Standard Java Mechanism** - Uses well-established JVM patterns
