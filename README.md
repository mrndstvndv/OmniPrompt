# Search (WIP)

Search is a work-in-progress Android universal search surface built with Kotlin and Jetpack Compose. It provides a fast command palette-style overlay that fans out queries across multiple providers while staying customizable for power users.

## Features
- Multi-provider querying that covers installed apps, calculator expressions, text utilities, on-device files with thumbnails, and web search fallbacks.
- Alias system for defining quick keywords that launch apps or reroute queries to preferred search engines.
- Provider ranking repository that tracks usage frequency, lets you reorder sources, and boosts the results you act on most.
- Material 3 UI with translucent results, adjustable blur/opacity, and motion-aware animations that respect accessibility preferences.
- Settings surface for per-provider tuning (e.g., web defaults, background behavior, loading indicators).

## Usage

### Building Locally

To build a signed release APK locally:

1. Generate a keystore (if you haven't already):
   ```bash
   keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias search
   ```

2. Create a `keystore.properties` file in the project root:
   ```properties
   STORE_FILE=/absolute/path/to/release.keystore
   STORE_PASSWORD=your_store_password
   KEY_ALIAS=your_key_alias
   KEY_PASSWORD=your_key_password
   ```

3. Run the release build command:
   ```bash
   ./gradlew assembleRelease
   ```
   The signed APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

### Setting up GitHub Actions

To enable automated signed builds on GitHub:

1. Go to your repository **Settings > Secrets and variables > Actions**.
2. Add the following repository secrets:
   - `KEYSTORE_BASE64`: The base64-encoded content of your keystore file.
     - Linux: `base64 -w 0 release.keystore`
     - macOS: `base64 -i release.keystore`
   - `KEYSTORE_PASSWORD`: Your keystore password.
   - `KEY_ALIAS`: Your key alias (e.g., `search`).
   - `KEY_PASSWORD`: Your key password.

Once configured, the **Build Release** workflow will run automatically when you push a tag starting with `v*` (e.g., `v1.0.0`), or you can trigger it manually from the Actions tab.
