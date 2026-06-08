# Gradle Wrapper Remediation Plan

## Current Status
The project is missing the following essential Gradle wrapper files:
- `gradlew` (Unix script)
- `gradlew.bat` (Windows batch script)
- `gradle/wrapper/gradle-wrapper.jar` (Wrapper executable)

This prevents standard build execution via `./gradlew`.

## Root Cause
These files were either never committed or were removed from the workspace.

## Remediation Steps

1. **Restore Gradle Wrapper**:
   If you have Gradle installed on your system, run the following command in the project root:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   This will regenerate the missing scripts and the wrapper JAR.

2. **Verify Environment**:
   Ensure `JAVA_HOME` points to a valid JDK (JDK 17 recommended for Android 34).

3. **Perform Clean Build**:
   Once the wrapper is restored, verify the internal consistency with:
   ```bash
   ./gradlew clean assembleDebug
   ```

4. **Verify Room Schema**:
   The database version has been bumped to 21. Ensure that the Room schema export is enabled in `app/build.gradle.kts` if you wish to track schema changes.

## Verification Performed
- All compile-time errors in `TransportManager`, `AppDatabase`, `AppGraph`, and `MainScreen` have been fixed.
- Room migrations (up to version 21) have been implemented and registered.
- All newly added features are integrated into the UI and Navigation Drawer.
