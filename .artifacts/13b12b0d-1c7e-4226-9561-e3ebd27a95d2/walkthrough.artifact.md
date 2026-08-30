# Walkthrough - Fixing StateFlow.distinctUntilChanged() Deprecation

I have fixed the build error caused by calling `distinctUntilChanged()` on a `StateFlow`. In Kotlin Coroutines, `StateFlow` already provides `distinctUntilChanged` behavior by default (it only emits values when they are different from the current value), so the operator is redundant and has been deprecated for `StateFlow`.

## Changes

### [MeshViewModel.kt](file:///D:/7thSem/app/src/main/java/com/example/viewmodel/MeshViewModel.kt)

- Removed the redundant `.distinctUntilChanged()` call on the `isOnline` StateFlow.
- Removed the redundant import `kotlinx.coroutines.flow.distinctUntilChanged`.

```diff
-import kotlinx.coroutines.flow.distinctUntilChanged
...
         viewModelScope.launch {
-            isOnline.distinctUntilChanged()
+            isOnline
                 .collect { online ->
```

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin` and confirmed the build finished successfully.

> [!TIP]
> `StateFlow` always filters out consecutive identical values. If you need to ensure the same value is processed again, consider using a `SharedFlow` or a regular `Flow` instead.