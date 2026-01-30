# Building

Instructions for building Search locally and setting up automated builds.

## Building Locally

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

## Setting up GitHub Actions

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
