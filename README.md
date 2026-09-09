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

**A delete is a real `DELETE`.** Moving the records off the phone protects them from
*accidents* — a wipe, a reinstall, a stray `adb` command. A delete the user chose is not an
accident, and a row kept behind a flag is not a delete. Deleting a tally cascades to its
entries.

**Undo still works**, because the app holds what it removed in memory for as long as the
snackbar is up and writes it back — the same entry ids, the same timestamps, and *only* the
rows that actually disappeared, so an undo cannot overwrite anything the delete never
touched. Once that snackbar is gone, so is the data.

**The trade-off, stated plainly:** the app needs to reach the backend. Off the VPN it shows
"No connection — is the VPN up?" and refuses rather than pretending to save. There is no
local write queue, deliberately — a second copy that silently diverges is how you get two
answers to "how much did I spend".

## Duplicating a tally

**Duplicate** (the ⋮ menu, on a card or inside a tally) makes a full copy — every entry,
with its original amounts and timestamps — so the copy opens showing exactly the balance
the original shows. That is the point: you fork a count in order to change it. Take the bag
out of the copy and the number it lands on is the answer to "what would this have been if I
hadn't paid for the bag", while the original stays untouched.

The copy is an ordinary tally, not a view of another one. Tallies never mix, and a fork is
no exception: nothing links the two afterwards, and deleting either leaves the other alone.
Entry ids are new — two rows may never share one — but the times are not, so both tallies
group under identical day headings and read line for line.

## Layout

| Path | What it is |
| :--- | :--- |
| `db/schema.sql` | The two tables. DDL only — no rows are ever committed. |
| `lib/db.ts` | mysql2 pool against the system MySQL (3306), same as veggieBox/lawSuits. |
| `lib/tallies.ts` | Every query. Times are UTC end to end — see below. |
| `lib/auth.ts` | The single bearer-token guard. A missing token refuses everything. |
| `app/api/` | The REST surface the phone talks to. |
| `mobile/shared/src/commonMain/` | Every screen, plus `data/TallyApi.kt`. Compose Multiplatform, so iOS-ready. |
| `mobile/app/` | The Android host: the base URL, the token and the system back button. |

Amounts are minor units (agorot) everywhere — a running balance must never accumulate
float error.

**The client owns the clock.** Nothing relies on `CURRENT_TIMESTAMP`: MySQL writes column
defaults in the *server's* zone (IDT here), which read back as UTC put every entry three
hours in the future. Every write passes an explicit UTC string instead.

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
