# tally

Money in, money out — across several independent tallies at once.

A tally is one thing you are keeping count of: a trip to the centre, a kitchen job, this
week. Each one holds its own entries, its own currency and its own running balance, and
they never mix. You can reset a tally back to zero and keep it, or delete it outright;
both are undoable while the snackbar is up.

## Where the code is

| Path | What it is |
| :--- | :--- |
| `mobile/shared/src/commonMain/` | Every screen. Compose Multiplatform, so it is iOS-ready. |
| `mobile/app/` | The Android host: a key-value store and the system back button. |
| `app/` | The Next.js side — a description page plus the feedback-lib routes. |

## State

All of it lives on the device, in platform key-value storage, serialised as JSON by
`TallyStore`. There is no account and no sync: a tally is written standing in a bus queue,
so it must not need a network. Amounts are held in minor units (agorot) — a running
balance must never accumulate float error.

## Build and install

```bash
cd mobile && ./gradlew assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n com.automatelinux.tally.dev/com.automatelinux.tally.MainActivity
```

Always the **dev** flavor while developing — `assembleDevDebug`, never `assembleDebug`.

The web side is managed by the daemon like any other app: `d startApp --app tally`
(dev 3141, prod 3140).
