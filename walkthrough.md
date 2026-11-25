# Security Summary

Audit keamanan, reliabilitas, sinkronisasi engine kuantitatif, dan kesesuaian UI telah diterapkan langsung pada workspace proyek Android Pattern-PRO (berbasis baseline `v1.0.9`, commit `f8f8d0b`) tanpa mengubah arsitektur utama ataupun menghapus fitur yang valid.

## 1. Eliminasi Credential Tertanam & Proteksi Build Release
- **Penghapusan Secret BuildConfig**: `METAAPI_ACCOUNT_ID` dan `METAAPI_AUTH_TOKEN` dihapus dari `.env.example` dan tidak lagi diinjeksi ke `BuildConfig`, resource, maupun generated source (`app/build/generated/source/buildConfig/.../BuildConfig.java` terverifikasi bebas field MetaApi).
- **Secrets Plugin & Guard Build**: `app/build.gradle.kts` menambahkan `METAAPI_ACCOUNT_ID` dan `METAAPI_AUTH_TOKEN` ke `secrets.ignoreList`, mengaktifkan `isMinifyEnabled = true` serta `isShrinkResources = true` pada `buildTypes.release` (didukung `app/proguard-rules.pro` tanpa mengubah `signingConfigs` atau `debug.keystore`), serta mendaftarkan task Gradle `:app:verifyNoEmbeddedSecrets` yang berjalan setelah `generateDebugBuildConfig` / `generateReleaseBuildConfig` untuk menggagalkan build apabila ditemukan token JWT atau nilai non-placeholder MetaApi pada source, resource, maupun `BuildConfig`.
- **Pipeline CI/CD Terpisah**: `.github/workflows/android-release.yml` dipisahkan menjadi job `ci-verify` (pemindaian secret pada workspace & resource, `./gradlew :app:verifyNoEmbeddedSecrets :app:lintDebug :app:testDebugUnitTest`) dan job `publish-release` berizin minimal (`permissions: contents: read` di workflow level dan `contents: write` khusus `publish-release`) yang terproteksi `environment: release` dan tag `v*.*.*`, menjalankan `:app:assembleRelease` dengan production keystore dari GitHub Secrets, membersihkan file keystore sementara (`if: always()`), serta memublikasikan checksum `.sha256` APK.

## 2. Penyimpanan Kredensial Lokal Terenkripsi & Kontrol Backup
- **Enkripsi AES-256-GCM Berbasis Android Keystore**: `UserPreferencesManager` (`app/src/main/java/com/example/data/local/UserPreferencesManager.kt`) mengenkripsi `accountId` dan `authToken` ke file `hub_secure_credentials` menggunakan `AES/GCM/NoPadding` 256-bit dengan kunci di `AndroidKeyStore` (`KeyGenParameterSpec`, `setRandomizedEncryptionRequired(true)`). Pada lingkungan pengujian JVM Robolectric (`Build.FINGERPRINT == "robolectric"`), digunakan kunci acak 256-bit via `KeyGenerator` (bukan kunci hardcoded), sementara perangkat nyata wajib menggunakan Android Keystore (fail-closed).
- **Pembersihan Legacy Plaintext & Redaksi UI**: `purgeLegacyPlaintextCredentials()` otomatis menghapus sisa key plaintext lama di `hub_trader_user_prefs` saat `UserPreferencesManager.getInstance(...)`, simpan, baca, maupun `clearMetaApiCredentials()`. `HubViewModel` tidak pernah memuat token atau `accountId` mentah ke `HubUiState`, melainkan hanya status (`hasCustomSavedCredentials`) dan petunjuk tersamar (`savedMetaApiAccountIdHint` / `savedMetaApiTokenHint`). Input token pada `SettingsScreen` dan `HonestOfflineErrorState` (`MainActivity.kt`) memakai `PasswordVisualTransformation` dengan tombol show/hide sementara serta teks peringatan jujur terkait keterbatasan OS/perangkat root.
- **Redaksi URL, Log, dan Error**: `LiveMarketAndNewsService` menerapkan `maskCredentialId()`, `redactSensitiveUrl()`, dan `sanitizeSensitiveText()` agar `accountId` dan `authToken` tidak bocor ke endpoint UI, exception message, maupun log Logcat `HubViewModel`.
- **Backup & Device Transfer Exclusion**: `app/src/main/res/xml/backup_rules.xml` dan `app/src/main/res/xml/data_extraction_rules.xml` mengecualikan seluruh `sharedpref` (`hub_secure_credentials.xml`, `hub_trader_user_prefs.xml`, `.`) dari Auto Backup dan Device Transfer.

## 3. Penguatan Keamanan OTA GitHub Release
- **Allowlist & Validasi URL**: `LiveMarketAndNewsService` membatasi sumber OTA pada repositori resmi (`deckyp5758-eng/Pattern-PRO` / `BuildConfig.GITHUB_REPOSITORY` tervalidasi regex `REPO_SLUG_REGEX`) dan mewajibkan URL unduhan HTTPS terpercaya (`isTrustedOtaDownloadUrl`) sebelum mengarahkan pengguna atau mengunduh APK.
- **Anti-Downgrade & Verifikasi SHA-256 / Sertifikat Penandatangan**: `parseSemVer()` dan `compareSemVerTags()` memblokir pemasangan versi lebih lama secara default kecuali sakelar `allowOtaDowngrade` diaktifkan eksplisit di `SettingsScreen`. Metadata rilis membaca aset `.sha256` (`evaluateReleaseMetadata`), serta tersedia helper `computeFileSha256()` dan `verifyApkSha256AndSignature()` untuk memverifikasi integritas SHA-256 dan digest sertifikat APK via `PackageManager`.

## 4. Penyelarasan Mesin Kuantitatif, Konkurensi & Kejujuran UI
- **Non-Repaint & Pemisahan Waktu**: Sinyal final, struktur pasar, serta snapshot MTF tetap dikunci pada Closed Candle `[1]`, sementara polling Forming Candle `[0]` hanya memperbarui pemantauan berjalan dan memisahkan `serverDataTimeMs` dari `calculatedAtDeviceTimeMs` (`DataEngineReport`). `TradingSessionReport` memisahkan `currentSession` (`nowMs`) dari `candleSession` (`candleEpochSec`).
- **Status Berita 4-Keadaan & Kelengkapan MTF**: `NewsDataStatus` (`LIVE_RSS`, `LIVE_PLUS_CUSTOM`, `STALE_CACHE`, `OFFLINE_EMPTY`) menggantikan flag biner sehingga cache lama tidak pernah diklaim live. `MtfEngineReport` memeriksa kelengkapan `H1 + M30 + M15` (`isMtfDataComplete`); kekurangan TF wajib menetralkan `htfBias` dan memblokir klaim checklist penuh pada `TenEnginesScreen` serta `MtfMatrixScreen` (termasuk rendering 3-keadaan `TrendClassification`: `BULLISH` hijau, `BEARISH` merah, `NEUTRAL` amber `"— EMA"`).
- **Standby Suppression & Kejujuran Sizing**: Saat `verdict` berada pada status Standby/Netral (`TradeSignalScreen` dan `HudTerminalScreen`), angka Entry/SL/TP aktif dan tombol salin setup order penuh disembunyikan (`trade_standby_notice`), dan kalkulator risiko (`calculatePositionRisk`) membedakan secara tegas mode indikatif `unit` pada feed proxy non-broker versus mode lot MT5 terverifikasi broker (`BrokerContractSpec`).

---

## PoC Verification

Seluruh pembuktian verifikasi dijalankan langsung di workspace dengan hasil nyata sebagai berikut:

1. **Pemindaian Secret & Guard Build**:
   - `grep -rnE 'METAAPI_ACCOUNT_ID|METAAPI_AUTH_TOKEN|-----BEGIN .*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]{15,}' .env* app/src/main .github/workflows` tidak menemukan kredensial, token JWT, maupun private key tertanam (hanya konstanta pembersihan key lama, perbandingan placeholder, dan pola scanner CI).
   - `app/build/generated/source/buildConfig/debug/com/example/BuildConfig.java` diverifikasi tidak memiliki field `METAAPI_ACCOUNT_ID` atau `METAAPI_AUTH_TOKEN`.
   - `./gradlew :app:verifyNoEmbeddedSecrets` berstatus **PASSED**.

2. **Kompilasi Debug, Release Kotlin, Lint & AI Studio Build**:
   - `./gradlew :app:verifyNoEmbeddedSecrets :app:testDebugUnitTest :app:lintDebug :app:compileReleaseKotlin --no-daemon` selesai dengan status **BUILD SUCCESSFUL in 2m 37s**.
   - `:app:compileReleaseKotlin` **PASSED** (verifikasi kompilasi Kotlin varian release dengan aturan ProGuard/R8 aktif di konfigurasi).
   - `:app:lintDebug` **PASSED** dengan **0 errors** (73 non-fatal warnings pada laporan `app/build/reports/lint-results-debug.html`).
   - `compile_applet` **PASSED** (`Build succeeded - the applet is compiled`).

3. **Unit Test & Robolectric/Compose UI Regression Suite (39/39 Passed)**:
   - `AllFunctionsAuditTest`: **15 passed, 0 failed, 0 skipped**
   - `AuditVerificationTest`: **13 passed, 0 failed, 0 skipped**, memverifikasi:
     - Status berita 4-keadaan (`test01`)
     - Kelengkapan MTF `H1/M30/M15` (`test02`)
     - Rendering tren `NEUTRAL` di `MtfMatrixScreen` (`test03`)
     - Pemisahan `activeTimeframeVerdict` vs `mtfContextSummary` saat pullback (`test04`)
     - Kalkulasi proxy `unit` vs lot MT5 terverifikasi `BrokerContractSpec` vs fallback (`test05`)
     - Penekanan kartu Entry/SL/TP & tombol salin order saat Standby pada `TradeSignalScreen` (`test06`)
     - Pemisahan `currentSession` vs `candleSession` dan `serverDataTimeMs` vs `calculatedAtDeviceTimeMs` (`test07`)
     - Generation guard `HubViewModel` & penyamaran input kredensial tersimpan (`test08`)
     - Ketiadaan secret `BuildConfig`, enkripsi AES-GCM non-plaintext, pembersihan key plaintext lama, dan redaksi URL/error (`test09`)
     - Allowlist repo OTA, perbandingan SemVer, blokir downgrade, validasi HTTPS URL, & ekstraksi/verifikasi SHA-256 (`test10`)
     - Invariansi Non-Repaint Closed Candle `[1]` terhadap mutasi Forming Candle `[0]` (`test11`)
     - Pengecualian `sharedpref` di `backup_rules.xml` dan `data_extraction_rules.xml` (`test12`)
     - Tampilan Compose `SettingsScreen`, `NewsEngineScreen`, dan `TenEnginesScreen` (`test13`)
   - `ExampleRobolectricTest`: **6 passed, 0 failed, 0 skipped** (termasuk verifikasi koneksi server live Binance/Kraken/Yahoo/RSS & notifikasi)
   - `ExampleUnitTest`: **5 passed, 0 failed, 0 skipped**
