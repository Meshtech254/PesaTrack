# Device validation

Use only synthetic fixtures and a disposable test profile.

## XML/manual build first

- Install `app/build/outputs/apk/manual/debug/app-manual-debug.apk` with Play Protect enabled. Record the actual installation result; a successful build does not prove device acceptance.
- Confirm onboarding identifies XML/manual import and never requests SMS access. Import Center must have no permission, direct-scan or SMS-resume buttons; Settings must have no automatic SMS reconciliation toggle.
- Import `fixtures/synthetic-sms.xml` using All available history, reimport it and verify no duplicates. Add a manual transaction, edit it, set a PIN, and create/restore an encrypted backup.
- Verify manifest permissions contain neither READ_SMS nor RECEIVE_SMS, and no SmsReceiver is registered. The build verification also inspects the packaged APK for this contract.
- For a later update test, retain this installation and add synthetic records. Build the `sms` flavor with the same signing key and a higher version code, install it as an update, and verify records, onboarding settings and app-lock credentials persist. Do not uninstall or clear data. Back up the synthetic history before testing.

## Shared and SMS-enabled checks

- Install debug APK on Android 8 and a current Android device. Verify seven-step setup/import path and skip/manual path, empty dashboard, light/dark, rotation and large font sizes.
- Deny SMS permissions; verify meaningful unavailable status and successful XML/manual use. Test an installer that cannot allowlist SMS and ensure no workaround is suggested.
- If permitted, scan >200 synthetic SMS, cancel, rotate, kill/restart and resume. Rescan: transaction count must not increase. Revoke permission mid-scan and inspect status.
- Import `fixtures/synthetic-sms.xml`, then reimport. Inspect fee, balance observation, refund and cash withdrawal semantics. No synthetic records should appear before explicit import.
- Import the same referenced transaction through direct SMS and XML with missing subscription ID; it must deduplicate. Check novel no-reference formats manually.
- Deliver a multipart supported SMS, a bank advertisement, and an OTP. Only the supported transaction should be counted. Verify normal behavior under Doze and disabled SMS access without assuming immediate delivery.
- Change category, account, notes, amount, internal direction and exclusion. Rescan and verify edits survive. Search merchant/reference, filter dates/amounts and compare exact totals to fixtures.
- Add, rename, archive and merge accounts. Confirm only the latest balance observation is displayed, with uncertainty for unidentified accounts.
- Set weekly/monthly category limits and link actual savings records using the allocation selector; verify refunds and internal movements.
- Set PIN, retry wrong PIN five times, verify cooldown, wait one minute, cold restart, background and resume. Enroll biometrics, test cancellation/unavailability, and verify screenshot protection.
- Create encrypted backup, change a byte, attempt wrong passphrase, restore valid backup twice. Tampered/wrong backups must not write records. Restore on another device using only the passphrase. Export CSV with formula-leading merchant text and inspect escaping.
- Delete imported history: manual entries remain and retained SMS disappears. Clear all app data: relaunch starts empty without lock credentials or old key.
- Run `connectedManualDebugAndroidTest` (or `connectedSmsDebugAndroidTest` for the SMS flavor) for SQLCipher reopen, duplicate constraints, checkpoint rollback, 250-message XML cancellation/reimport, backup restore and wrapped-key stability.

Do not treat compilation or JVM unit tests as proof of device behavior or complete provider coverage.
