# tally

Money in, money out — across several independent tallies at once.

A tally is one thing you are keeping count of: a trip to the centre, a kitchen job, this
week. Each one holds its own entries, its own currency and its own running balance, and
they never mix.

## Where the records live

**In the `tally` MySQL database, not on the phone.** The device holds a view, not the only
copy. An app whose records live only in its own storage loses them to a wipe, a reinstall,
a lost phone — or to anyone with `adb` — and money records must survive all four. Proven:
`adb shell pm clear com.automatelinux.tally.dev` removes every byte the app holds locally
and the tallies are still there on next launch.

**Nothing is ever hard-deleted.** Reset and delete set `deleted_at`; the reset also stamps
a `reset_batch` so undoing one reset cannot resurrect entries the user had deleted
individually beforehand. No query in `lib/tallies.ts` issues a `DELETE`. Undo is therefore
a fact about the data, not a five-second window in the UI.

**The trade-off, stated plainly:** the app needs to reach the backend. Off the VPN it shows
"No connection — is the VPN up?" and refuses rather than pretending to save. There is no
local write queue, deliberately — a second copy that silently diverges is how you get two
answers to "how much did I spend".

## Layout

| Path | What it is |
| :--- | :--- |
| `db/schema.sql` | The two tables. DDL only — no rows are ever committed. |
| `lib/db.ts` | mysql2 pool against the system MySQL (3306), same as veggieBox/lawSuits. |
| `lib/tallies.ts` | Every query, each filtered on `deleted_at IS NULL`. |
| `lib/auth.ts` | The single bearer-token guard. A missing token refuses everything. |
| `app/api/` | The REST surface the phone talks to. |
| `mobile/shared/src/commonMain/` | Every screen, plus `data/TallyApi.kt`. Compose Multiplatform, so iOS-ready. |
| `mobile/app/` | The Android host: the base URL, the token and the system back button. |

Amounts are minor units (agorot) everywhere — a running balance must never accumulate
float error.

## Auth

The service listens on `0.0.0.0` (WireGuard *and* the home LAN), so every route needs
`Authorization: Bearer $TALLY_API_TOKEN`. The server reads it from `.env.local`; the APK
gets the same value baked in at build time from the gitignored `mobile/.env`. Both are
untracked. A build with no token says so on screen instead of failing quietly.

## Build and install

```bash
cd mobile && ./gradlew assembleDevDebug          # always the dev flavor while developing
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n com.automatelinux.tally.dev/com.automatelinux.tally.MainActivity
```

The web side is managed by the daemon like any other app: `d startApp --app tally`
(dev 3141, prod 3140). Schema changes go in `db/schema.sql` and are applied with
`sudo mysql tally < db/schema.sql` — it is written to be re-runnable.
