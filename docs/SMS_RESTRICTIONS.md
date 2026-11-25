# Android SMS access

## XML/manual version

The `manual` build flavor has no `READ_SMS` or `RECEIVE_SMS` permissions and no SMS broadcast receiver. It imports only a user-selected XML file or manually entered records. Its interface does not offer permission requests, direct scans, scan resume, or periodic SMS reconciliation. SMS worker entry points are also guarded, and old scheduled SMS work is cancelled on startup.

The `sms` flavor adds the SMS permissions and receiver from `app/src/sms/AndroidManifest.xml` and enables the direct-SMS interface. It is kept separately for a later update. Both flavors share an application ID and database; an update must use the same signing certificate and a higher version code. See the README for commands and signing details.

Removing these permissions addresses Google's documented sensitive-permission sideloading trigger, but does not guarantee a particular Play Protect decision. Keep Play Protect enabled. Reference: [Google Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance#app-blocked).

## SMS-enabled version

READ_SMS and RECEIVE_SMS are hard-restricted Android permissions. The installer of record must allowlist them, and the user must consent at runtime. A sideloaded APK is not automatically eligible. PesaTrail requests only reading and receipt, never SEND_SMS, contact permissions, broad storage access, or the default-SMS role. It cannot bypass installer or OS restrictions.

Historical reading uses Telephony.Sms.CONTENT_URI with a date range and an increasing row-ID checkpoint in bounded batches. WorkManager persists a unique import job; encrypted scan records persist progress. Each transaction and checkpoint commit together. Process death can replay a message safely through unique constraints. Permission denial/revocation appears in scan status, with XML import still available. WorkManager may stop a long-running scan; resume from Import Center.

SMS_RECEIVED is the supported non-default-app broadcast when RECEIVE_SMS is available. Multipart text is joined and processed within a bounded goAsync window; pending completion is always released. Delivery is subject to OEM/Android restrictions. Failure schedules a permitted reconciliation scan. Optional periodic reconciliation runs at a requested 12-hour interval, with Android controlling the actual timing; it is not guaranteed real time. The app does not request battery-optimization exemptions or run a permanent service.

The file fallback uses ACTION_OPEN_DOCUMENT for user-selected SMS Backup & Restore XML (`sms` elements with `address`, `body`, millisecond `date`, optional `sub_id`). It does not access other files or upload anything. XML document declarations are rejected; files are processed with a streaming parser, transaction/message limits, cancellation checks and duplicate constraints. Reselect a partially processed file to resume safely.

Google Play SMS permission eligibility is separate from what Android allows on a personally installed device. Personal ownership or a financial-use explanation alone does not guarantee Play approval or installer allowlisting. Review the actual installer/device policy. Do not instruct users to weaken OS protections.

Reference: [Android Manifest.permission](https://developer.android.com/reference/android/Manifest.permission), specifically READ_SMS and RECEIVE_SMS.
