---
name: search-build
description: Search app build configuration - debug/release build types with applicationIdSuffix, Compose/BuildConfig/AIDL build features, required permissions (INTERNET, READ_CONTACTS, PACKAGE_USAGE_STATS, MANAGE_EXTERNAL_STORAGE, Shizuku, Termux), gradle commands (compileDebugKotlin, assembleDebug, testDebugUnitTest).
---

# Build Configuration

## Build Types

| Type      | Suffix    | Minification | App Name       |
|-----------|-----------|--------------|----------------|
| `debug`   | `.debug`  | No           | "Search Debug" |
| `release` | None      | Yes (R8)     | "Search"       |

## Key Build Features

```kotlin
// Location: app/build.gradle.kts

android {
    buildFeatures {
        compose = true      // Jetpack Compose
        buildConfig = true  // BuildConfig generation
        aidl = true         // AIDL for Shizuku IPC
    }
}
```

## SDK Versions

```
compileSdk: 36
minSdk: 24 (Android 7.0 Nougat)
targetSdk: 36
```

## Important Permissions

| Permission                        | Purpose                          |
|-----------------------------------|----------------------------------|
| `INTERNET`                        | Favicon loading, web features    |
| `READ_CONTACTS`                   | Contact search                   |
| `READ_PHONE_STATE`                | SIM number display               |
| `READ_PHONE_NUMBERS`              | Phone number access              |
| `PACKAGE_USAGE_STATS`             | Recent apps tracking             |
| `MANAGE_EXTERNAL_STORAGE`         | File search (all files)          |
| `WRITE_SECURE_SETTINGS`           | Dev options toggle (Shizuku)     |
| `com.termux.permission.RUN_COMMAND`| Termux integration              |

### Permission Locations

Permissions are declared in `app/src/main/AndroidManifest.xml`

## Gradle Commands

```bash
# Compile Kotlin (required after any .kt file change)
./gradlew :app:compileDebugKotlin

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run tests
./gradlew :app:testDebugUnitTest

# Clean build
./gradlew clean

# Check dependencies
./gradlew :app:dependencies
```

## Version Catalog

Dependencies are managed via Gradle Version Catalog:

| File                          | Purpose                    |
|-------------------------------|----------------------------|
| `gradle/libs.versions.toml`   | Dependency version catalog |
| `app/build.gradle.kts`        | App-level build config     |
| `build.gradle.kts`            | Root build config          |
| `settings.gradle.kts`         | Module settings            |
| `gradle.properties`           | Version info (0.0.1)       |

## Adding a Dependency

1. **Add to version catalog** (`gradle/libs.versions.toml`):
   ```toml
   [versions]
   newLib = "1.0.0"
   
   [libraries]
   new-lib = { group = "com.example", name = "lib", version.ref = "newLib" }
   ```

2. **Add to build.gradle.kts**:
   ```kotlin
   dependencies {
       implementation(libs.new.lib)
   }
   ```

## Key Dependencies

| Dependency           | Version  | Purpose                    |
|----------------------|----------|----------------------------|
| Kotlin               | 2.0.21   | Language                   |
| Compose BOM          | 2024.11+ | UI Framework               |
| Material 3           | 1.5.0-α8 | Design System              |
| Room                 | 2.6.1    | Database (FTS4)            |
| Coroutines           | 1.7.3    | Async                      |
| WorkManager          | 2.9.1    | Background Work            |
| Shizuku              | 13.1.5   | Elevated Permissions       |

## Shizuku Integration

Shizuku is used for elevated permissions (e.g., toggling Developer Options):

| File                              | Purpose                    |
|-----------------------------------|----------------------------|
| `aidl/.../IUserService.aidl`      | IPC interface definition   |
| `UserService.kt`                  | Shizuku service impl       |

## Related Skills

- `search-overview` - Project structure
- `search-guides` - Adding permissions and dependencies
