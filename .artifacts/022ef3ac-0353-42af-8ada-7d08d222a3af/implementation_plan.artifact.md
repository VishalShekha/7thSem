# Fix Cloud Sync SocketTimeoutException

The application is failing to sync with the cloud because it's attempting to connect to a hardcoded default IP address (`172.20.10.5:3000`) that is unreachable. This results in a `java.net.SocketTimeoutException`.

## User Review Required

> [!IMPORTANT]
> I will be adding a Settings interface to allow you to configure the Cloud Sync Server URL. This is necessary because the default IP is often incorrect for different network environments.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MeshViewModel.kt](file:///D:/7thSem/app/src/main/java/com/example/viewmodel/MeshViewModel.kt)
- Add `syncStatus` StateFlow to track the last sync result (Success, Failed, In Progress).
- Add `updateServerUrl(url: String)` function to call `RetrofitClient.setBaseUrl`.
- Add `getServerUrl(): String` function.
- Update `syncToCloud()` to update `syncStatus`.

#### [MODIFY] [Screens.kt](file:///D:/7thSem/app/src/main/java/com/example/ui/screens/Screens.kt)
- Update `ProfileScreen` to include a "Cloud Settings" section.
- Add a TextField to edit the Server URL.
- Show the current `syncStatus`.

#### [MODIFY] [MainActivity.kt](file:///D:/7thSem/app/src/main/java/com/example/MainActivity.kt)
- Add a "Settings" option to the navigation or update the `ProfileScreen` integration.
- Currently, `ProfileScreen` is not in the `NavHost`. I'll add it.

## Verification Plan

### Manual Verification
- Deploy the app.
- Go to the new Settings/Profile screen.
- Change the URL to a valid local server IP (e.g., `10.0.2.2:3000` for emulator).
- Verify that sync succeeds (if server is running) or at least shows a meaningful error in the UI instead of just a crash/timeout in logs.
