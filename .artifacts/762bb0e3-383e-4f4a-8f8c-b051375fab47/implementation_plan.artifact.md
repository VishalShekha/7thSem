# Fix Missing Keystore Build Error

The project fails to build because the `debug` build type is configured to use a custom signing configuration (`debugConfig`) which points to a missing `debug.keystore` file in the project root. This file is ignored by Git, so it may be missing in some environments.

## Proposed Changes

### [Component Name] :app module build configuration

#### [MODIFY] [build.gradle.kts](file:///D:/7thSem/app/build.gradle.kts)

Modify the `debug` build type configuration to only use `debugConfig` if the `debug.keystore` file actually exists. Otherwise, allow it to fall back to the default Android debug signing configuration.

```kotlin
    debug {
      val debugKeystore = file("${rootDir}/debug.keystore")
      if (debugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
      // If the condition is false, it will fall back to the default debug signing config.
    }
```

## Verification Plan

### Automated Tests
- Run `./gradlew :app:validateSigningDebug` (or a similar task that triggers signing validation) to ensure the build no longer fails when the file is missing.
- Since I'm in an IDE, I'll attempt a build/sync.

### Manual Verification
- Verify that the project syncs and builds successfully in Android Studio.
