# APK Vault — Android native starter

This project is a minimal Java Android app with:
- a welcome dialog shown on launch;
- local APK file selection;
- a 200 MB file-size limit;
- SHA-256 calculation;
- APK package name, app label, version name and version code when Android can parse metadata;
- explicit confirmation before handing the selected APK to Android's system installer.

## Build from a phone browser (cloud build)

1. Create a GitHub account if needed.
2. In GitHub, create a new repository named `apk-vault-native`.
3. Upload the contents of this ZIP to the repository root (make sure `.github/workflows/build-apk.yml` is included).
4. Open the repository's **Actions** tab and select **Build APK Vault**.
5. Tap **Run workflow** if it is not already running from the upload.
6. Open the completed workflow run. Under **Artifacts**, download `APKVault-debug`.
7. Extract the artifact ZIP on your phone; it contains `app-debug.apk`.

The included workflow builds on GitHub-hosted Linux runners, not on the phone. GitHub Actions availability/usage limits depend on account and repository settings.

## Local build requirements

- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3
- Gradle 8.9

## Important limitations and safety

- This is a starter project, not a malware scanner. SHA-256 identifies file bytes; it does not prove safety.
- Metadata can be read even for a malicious APK. Only install apps from sources you trust.
- The app never silently installs another APK. The user must approve Android's system installer.
- Android may require enabling "Allow from this source" for APK Vault. Grant it only if you choose to use this function.
- The selected APK is copied to the app's private cache, with a 200 MB limit. Cache can be cleared by Android.
- APK installer behavior can differ by Android vendor/version. Test with a harmless APK first.
- `targetSdk 28` is intentionally used here to reduce build complexity for a starter. For distribution, update target SDK to current Play requirements and retest all installer behavior. This app is not configured for Google Play publication.
- Do not use the app to install software without the device owner's permission.
