# Fix missing debug keystore error

The project build is failing because it expects a `debug.keystore` file in the root directory, which is missing. This is caused by a custom `debugConfig` in `app/build.gradle.kts` that is assigned to the `debug` build type.

## User Review Required

> [!IMPORTANT]
> I am proposing to remove the custom `debugConfig` and its usage in the `debug` build type. This will cause Gradle to use the default Android debug keystore (typically located in `~/.android/debug.keystore`). If you have a specific requirement for a custom debug keystore at `D:\7thSem\debug.keystore`, please let me know.

## Proposed Changes

### app component

#### [MODIFY] [build.gradle.kts](file:///D:/7thSem/app/build.gradle.kts)

- Remove the `debugConfig` block from `signingConfigs`.
- Remove `signingConfig = signingConfigs.getByName("debugConfig")` from the `debug` build type.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify the build succeeds.
