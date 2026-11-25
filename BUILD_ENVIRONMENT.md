# Build Environment Requirements — Pattern-PRO

## Toolchain & SDK
- **JDK**: Java 21 (Temurin / OpenJDK 21+)
- **Gradle Wrapper**: `./gradlew` (Gradle 9.3.1 — committed at root with `gradle/wrapper/gradle-wrapper.jar`)
- **Android Gradle Plugin**: Configured via `gradle/libs.versions.toml`
- **Compile SDK / Target SDK**: `36` (`compileSdk = 36`, `targetSdk = 36`)
- **Min SDK**: `24`
- **Kotlin**: `2.2.21`

## Build & Verification Commands
```bash
chmod +x ./gradlew
./gradlew :app:verifyNoEmbeddedSecrets
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

## Release Signing Requirements
Release builds (`assembleRelease`) require production signing material injected at CI/build time via protected environment secrets (never committed to source control):
- `KEYSTORE_BASE64`: Base64-encoded `.jks` release keystore (alias `upload`)
- `KEYSTORE_PATH`: Path to decoded keystore file (defaults to `${rootDir}/my-upload-key.jks`)
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password

MetaApi credentials (`METAAPI_ACCOUNT_ID`, `METAAPI_AUTH_TOKEN`) MUST NEVER be injected at build time or stored in `.env` / `BuildConfig`. End users enter their own credentials inside the installed application, where they are encrypted using Android Keystore (AES-256-GCM).
