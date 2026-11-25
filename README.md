# PesaTrail

Native, offline personal money tracker for Kenya. Kotlin, Compose Material 3, MVVM, Hilt, encrypted Room/SQLCipher, DataStore, WorkManager, Android Keystore and BiometricPrompt. Minimum Android 8 (API 26). This folder is the Android Studio project; it is not a website.

## Build

Use Android Studio with JDK 21 (the bundled `jbr` works), Android SDK platform 36 and build tools. The app compiles Java/Kotlin bytecode for Java 17. Set `sdk.dir` in your uncommitted `local.properties`, or set `ANDROID_HOME`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testManualDebugUnitTest :app:assembleManualDebug :app:assembleManualRelease :app:assembleManualDebugAndroidTest :app:lintManualDebug
```

On macOS/Linux use `./gradlew`. Initial builds need internet to fetch dependencies; the installed app has no INTERNET permission and runs entirely offline.

Debug APK: `app/build/outputs/apk/manual/debug/app-manual-debug.apk`.
Unsigned release APK: `app/build/outputs/apk/manual/release/app-manual-release-unsigned.apk`.
Unit reports: `app/build/reports/tests/testManualDebugUnitTest/index.html`.
With a connected Android device or emulator: `./gradlew connectedManualDebugAndroidTest`.

The project pins SDK 36 to the locally installed stable Android platform. Check Android SDK release notes before upgrading; a newer target also requires its SDK and a compatible Android Gradle Plugin. SDK 37 behavior documentation alone does not establish a stable SDK installation.

## Build variants and future updates

Use the **manualDebug** build variant in Android Studio for the installable XML/manual version. It supports local XML import, manual transactions, reports, encrypted storage, backups and app lock. It declares no SMS permissions or SMS receiver and does not schedule SMS scans.

The separate **sms** flavor preserves direct SMS scanning and receipt for a later build. To build it later, use `:app:assembleSmsDebug` or `:app:assembleSmsRelease`; Android and Play Protect restrictions still apply. Do not use the aggregate `assembleDebug` command when you only want the manual APK, because it builds both flavors.

Both flavors use application ID `ke.pesatrail` and the same database schema. The manual build is version 1.0.1 (code 2); the prepared SMS build is version 1.1.0 (code 3). They replace one another rather than installing side by side. An update must have a higher version code and the same signing certificate to preserve installed app data. Future releases must keep increasing the version code.

The provided manual debug APK uses this machine's existing development key. A future SMS debug APK built with that same key can update it. A production release signed with a different private key cannot update a debug installation directly. Keep encrypted backups, and preserve the original signing key. Do not uninstall the app just to apply an update: uninstalling removes its local history and protected database key.

## Using the app

1. Complete or skip onboarding. No account or registration is required.
2. Open Import Center and choose a local SMS Backup & Restore XML file through Android's document picker. The XML/manual build does not request SMS access. The separate SMS-enabled build can request direct SMS access when the installer and Android permit it.
3. Choose 30 days, 90 days, one year, all history, or custom dates. Scans report processed/imported/duplicate/unsupported/failed counts and preserve a checkpoint. Cancel and resume direct scans; reselect XML after interruption to safely retry.
4. Review unmatched messages locally. Rules authorize an exact sender plus a matching phrase, category, and direction. Conservative completion/amount/OTP checks still apply. Unsupported layouts remain reviewable rather than producing invented records.
5. Correct records, add notes and custom categories, mark internal movements, exclude records, or delete them. Category choices are remembered per merchant. Manual records are available using the + button.
6. Add and rename accounts, assign ambiguous imports to their actual account, archive accounts, and merge their history. Account IDs remain stable when names change.
7. Use Home and Analytics for exact financial totals, period spending, interactive charts, category limits, and savings allocations. Transactions supports text, amount, date, direction, provider, category and account filters. Export writes the filtered selection as CSV after an explicit document-picker action.
8. Enable biometric unlock or a 6–12 digit PIN. App lock activates after one minute without interaction. Cold starts require unlocking. Keep a portable backup if you enable a PIN.

## Data and financial semantics

- Monetary storage and calculations use signed 64-bit integer cents; parsing uses exact `BigDecimal`. Floating point is used only for chart rendering/progress ratios.
- Income excludes loan disbursements and internal movements. Expenses include outgoing payments and fees; refunds offset spending. Withdrawals/deposits represent movement to/from cash, not automatic expenditure/income. Mark transfers between your own accounts INTERNAL to exclude their principal.
- Embedded fees are counted once. A standalone charge's amount is counted as a fee rather than counted twice as amount plus fee.
- Account cards use the latest timestamped SMS balance per account, never the sum of historical balances. These are dated observations, not live balances. Wallets without reliable IDs are clearly marked unidentified; split them into actual accounts before relying on balances. There is no fabricated aggregate balance.
- Without an explicit transaction date, the SMS received timestamp is used and flagged. Missing fee/balance/reference/identity stays unknown.
- Deduplication uses provider + reference + event type when available, plus SHA-256 normalized SMS fingerprints. Reference matches work across XML/direct import and missing SIM IDs. Without references, received timestamps distinguish recurring identical messages. Unknown SIM/account identities remain uncertain; review cross-source duplicates without transaction references.
- Reversal/refund receipts reduce spending. Loans do not become earnings. Mixed messages representing multiple monetary events (e.g. some Fuliza settlements), fee refunds, ambiguous direction, foreign currencies and novel bank formats need review and correction; the parser does not claim coverage of every provider's formats. Use exclude/manual corrections for mismatches.
- Savings progress only counts real records explicitly linked through the Savings allocation selector in transaction details. This is an allocation ledger, not a guarantee that funds remain available.

## Privacy and encryption

The entire Room database, including original source SMS, review messages, merchant rules, accounts, budgets and goals, is encrypted using SQLCipher. A random 256-bit database secret is wrapped using an AES-GCM key held by Android Keystore. Only the wrapped secret is stored in private no-backup storage. Missing/corrupt keys fail closed rather than replacing a key on an existing database. Room schema JSON is exported for migration review; version 1 is the initial schema, so no destructive fallback or fictitious migration is supplied. Future schema versions must add tested migrations.

Backups use a separate user passphrase: PBKDF2-HMAC-SHA256 (600,000 iterations, random 16-byte salt) and AES-256-GCM (random 12-byte nonce, authenticated header). No encryption key/passphrase is put beside the backup. Restore authenticates the entire file before writing and merges atomically. Backups cover transactions, accounts, categories/mappings, rules, review items, budgets and goals; device-specific Keystore keys, PIN, biometric settings and scan jobs are deliberately not portable. Restore requires a passphrase of at least 12 characters. Maximum encrypted restore file is 50 MiB.

PIN hashes are salted PBKDF2; biometric unlock uses Android's strong biometric check. This is a UI access gate over an encrypted app database, not a biometric-bound database key: permitted background imports can write while the UI is locked. Screenshot/recents capture is blocked with FLAG_SECURE. Android automatic backup and device transfer are excluded. No SMS bodies or amounts are logged by app code. Decrypted content necessarily exists temporarily in process memory; JVM Strings cannot be reliably wiped. A rooted/compromised OS is outside this design's protection boundary.

Exported CSV is plaintext and contains only the selected records; source SMS is excluded. Formula-leading text cells are escaped. Store exports accordingly. Delete imported history preserves manual records; delete all data uses Android's application data-clear API.

## Tests and release readiness

Synthetic unit fixtures exercise M-Pesa variants, configured bank senders, income/outgoing, fees, balances, OTP/notice rejection, refund/loan/internal-transfer semantics, exact currency arithmetic, fingerprints and backup authentication/tampering. Android integration tests exercise encrypted reopen, duplicate imports, rollback/checkpoints, >200-message XML imports, cancellation/reimport, backup restore, manual edit preservation and Keystore wrapping. No real financial SMS is committed.

Run device integration tests before trusting the app with your financial history. SMS installer allowlisting, biometric enrollment, large-history background limits, OEM broadcast delivery and permission revocation require actual device checks. The initial implementation is a buildable personal-use application, not an audited fintech product or a claim that every format from each named institution is supported. See `docs/DEVICE_TESTS.md` for the release checklist and `docs/BUILD_STATUS.md` for actual verification results from this workspace.

## Release signing

Release builds intentionally have no built-in signing secret. In Android Studio use **Build → Generate Signed App Bundle / APK → APK**, create a private keystore outside the repository, and store its passwords securely. Keep that key to install updates without deleting existing app data.

Alternatively build `assembleManualRelease`, then use SDK `zipalign` and `apksigner`:

```powershell
zipalign -p -f 4 app/build/outputs/apk/manual/release/app-manual-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks C:\private\pesatrail.jks --ks-key-alias pesatrail --out PesaTrail-release.apk app-release-aligned.apk
apksigner verify --verbose PesaTrail-release.apk
```

Let apksigner prompt for passwords. Never commit a keystore/password or sign a production release with the debug key.

## Official references

- [Android SMS permission restrictions](https://developer.android.com/reference/android/Manifest.permission#READ_SMS)
- [SMS received broadcast](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents#SMS_RECEIVED_ACTION)
- [WorkManager scheduling](https://developer.android.com/topic/libraries/architecture/workmanager)
- [SQLCipher Android Room integration](https://github.com/sqlcipher/sqlcipher-android#sqlcipher-for-android-room-integration)
- [Vico chart examples](https://guide.vico.patrykandpatrick.com/cartesian-charts/starter-examples)

Implementation layout: `domain/` for parsing and financial semantics, `data/` for Room/repository/preferences/backups, `ingest/` for SMS/file/background work, `security/` for key protection/portable cryptography, and `ui/` for Compose screens and the lifecycle-scoped ViewModel.
