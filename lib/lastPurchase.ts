import "server-only";
import { randomBytes } from "node:crypto";
import { q, exec } from "@/lib/db";
import { pushToPhones } from "@/lib/push";

/**
 * "Add my last purchase."
 *
 * You pay, tap the button, and the purchase lands in the tally with its shop, amount
 * and time. The card company is the source: tazrim already holds the Cal login and
 * answers GET /api/card/recent (/opt/dev/tazrim), so tally never logs in itself.
 *
 * A purchase reaches Cal a few minutes after the payment, so a press usually finds
 * nothing yet. The press then becomes a WAIT, recorded in `purchase_watches`: the
 * server keeps asking every two minutes for half an hour, adds the purchase the moment
 * it appears and wakes the phone. The phone can be closed meanwhile.
 *
 * Which purchase is "the last one": the newest card purchase that
 *  - was made no earlier than RECENT_MS before the press (you tap right after paying;
 *    something older is not what you just bought), and
 *  - is not in the tally already — neither taken from the card before (`card_ref`)
 *    nor written by hand (same amount, within SAME_TIME_MS), and
 *  - is not a later payment of an old installment purchase (Cal lists payment 11 of 12
 *    on the day it is charged, which is not something you bought today).
 */

const RECENT_MS = 30 * 60_000;
const SAME_TIME_MS = 5 * 60_000;
const CHECK_EVERY_MS = 2 * 60_000;
const WAIT_FOR_MS = 30 * 60_000;

export type CardPurchase = {
  date: string;
  amount: number;
  description: string;
  category: string;
  pending: boolean;
  memo: string;
  installment: { number: number; total: number } | null;
};

export type WatchStatus = "waiting" | "added" | "expired" | "failed" | "cancelled";

export type Watch = {
  id: string;
  tallyId: string;
  requestedAt: number;
  status: WatchStatus;
  checks: number;
  error: string | null;
  /** Until when it keeps asking. */
  until: number;
  entry: { id: string; amount: number; note: string; category: string; at: number } | null;
};

// Times are UTC end to end, as everywhere in tally (lib/tallies.ts).
const ms = (s: string) => new Date(s.replace(" ", "T") + "Z").getTime();
const sqlTime = (epochMs: number) => new Date(epochMs).toISOString().slice(0, 19).replace("T", " ");
const newId = () => Date.now().toString(36) + "-" + randomBytes(4).toString("hex").slice(0, 5);

export const refOf = (p: CardPurchase) => `${p.date}|${p.amount.toFixed(2)}|${p.description}`.slice(0, 200);

/** Cal's categories onto tally's buckets (mobile/.../data/Models.kt ExpenseCategories). */
export const CATEGORY: Record<string, string> = {
  "מזון ומשקאות": "food",
  "מזון מהיר": "food",
  "מסעדות": "food",
  "אירוח": "stay",
  "מלונאות ואירוח": "stay",
  "רכב ותחבורה": "travel",
  "אנרגיה": "travel",
  "אופנה": "shop",
  "ריהוט ובית": "shop",
  "ציוד ומשרד": "shop",
  "תקשורת ומחשבים": "shop",
  "טיפוח ויופי": "shop",
  "רפואה ובריאות": "shop",
  "תעשיה ומכירות": "shop",
  "בתי כלבו": "shop",
  "פנאי בילוי": "fun",
  "מוסדות": "bills",
  "פיננסים": "bills",
  "מקצועות חופשיים": "bills",
};

function tazrimUrl(): string {
  const url = process.env.TAZRIM_URL ?? "";
  if (!url) throw new Error("TAZRIM_URL is not set on the tally server (.env.local)");
  return url.replace(/\/$/, "");
}

/** tazrim's answer, or a thrown error that says what went wrong in words. */
export async function cardPurchases(): Promise<CardPurchase[]> {
  const res = await fetch(`${tazrimUrl()}/api/card/recent`, { signal: AbortSignal.timeout(150_000) });
  // An idle tazrim dev server answers with a "Starting…" page while it boots.
  if (res.status === 503) throw new Error("tazrim is starting up");
  const body = (await res.json().catch(() => null)) as
    | { ok: boolean; error?: string; purchases?: CardPurchase[] }
    | null;
  if (!body) throw new Error(`tazrim answered HTTP ${res.status} without JSON`);
  if (!body.ok) throw new Error(body.error ?? `tazrim answered HTTP ${res.status}`);
  return body.purchases ?? [];
}

async function pick(tallyId: string, requestedAt: number, purchases: CardPurchase[]) {
  const rows = await q<{ amount: number; at: string; card_ref: string | null }>(
    `SELECT amount, at, card_ref FROM entries WHERE tally_id = ? AND direction = 'OUT'`,
    [tallyId],
  );
  const refs = new Set(rows.map((r) => r.card_ref).filter(Boolean));
  const notBefore = requestedAt - RECENT_MS;

  return purchases
    .filter((p) => !(p.installment && p.installment.number > 1))
    .filter((p) => Date.parse(p.date) >= notBefore)
    .filter((p) => !refs.has(refOf(p)))
    .filter((p) => {
      const agorot = Math.round(p.amount * 100);
      const at = Date.parse(p.date);
      return !rows.some((r) => Number(r.amount) === agorot && Math.abs(ms(r.at) - at) <= SAME_TIME_MS);
    })
    .sort((a, b) => b.date.localeCompare(a.date))[0];
}

async function addFromCard(tallyId: string, p: CardPurchase): Promise<string> {
  const id = newId();
  await exec(
    `INSERT INTO entries (id, tally_id, direction, amount, note, category, at, vat_included, card_ref)
          VALUES (?, ?, 'OUT', ?, ?, ?, ?, 0, ?)`,
    [id, tallyId, Math.round(p.amount * 100), p.description.slice(0, 255), CATEGORY[p.category] ?? "other",
     sqlTime(Date.parse(p.date)), refOf(p)],
  );
  return id;
}

export async function getWatch(id: string): Promise<Watch | null> {
  const rows = await q<{
    id: string; tally_id: string; requested_at: string; status: WatchStatus; checks: number;
    error: string | null; entry_id: string | null; amount: number | null; note: string | null;
    category: string | null; at: string | null;
  }>(
    `SELECT w.id, w.tally_id, w.requested_at, w.status, w.checks, w.error, w.entry_id,
            e.amount, e.note, e.category, e.at
       FROM purchase_watches w LEFT JOIN entries e ON e.id = w.entry_id
      WHERE w.id = ?`,
    [id],
  );
  const r = rows[0];
  if (!r) return null;
  const requestedAt = ms(r.requested_at);
  return {
    id: r.id,
    tallyId: r.tally_id,
    requestedAt,
    status: r.status,
    checks: r.checks,
    error: r.error,
    until: requestedAt + WAIT_FOR_MS,
    entry: r.entry_id && r.at
      ? { id: r.entry_id, amount: Number(r.amount), note: r.note ?? "", category: r.category ?? "other", at: ms(r.at) }
      : null,
  };
}

/** The tally's newest press, so a phone that was closed can see how its wait ended. */
export async function latestWatch(tallyId: string): Promise<Watch | null> {
  const rows = await q<{ id: string }>(
    `SELECT id FROM purchase_watches WHERE tally_id = ? ORDER BY requested_at DESC LIMIT 1`,
    [tallyId],
  );
  return rows[0] ? getWatch(rows[0].id) : null;
}

// One timer per waiting watch, kept on globalThis so a dev-server reload of this module
// does not start a second one beside it.
const timers: Map<string, ReturnType<typeof setTimeout>> =
  ((globalThis as { _tallyWatchTimers?: Map<string, ReturnType<typeof setTimeout>> })._tallyWatchTimers ??= new Map());

function schedule(id: string, delay: number) {
  clearTimeout(timers.get(id));
  timers.set(id, setTimeout(() => void check(id), delay));
}

async function finish(id: string, status: WatchStatus, fields: { entryId?: string; error?: string } = {}) {
  timers.delete(id);
  await exec(
    `UPDATE purchase_watches SET status = ?, entry_id = COALESCE(?, entry_id), error = ? WHERE id = ? AND status = 'waiting'`,
    [status, fields.entryId ?? null, fields.error?.slice(0, 255) ?? null, id],
  );
}

async function tallyName(tallyId: string) {
  return (await q<{ name: string }>(`SELECT name FROM tallies WHERE id = ?`, [tallyId]))[0]?.name ?? "";
}

/** One look at the card. Ends the wait when it finds the purchase or runs out of time. */
async function check(id: string): Promise<void> {
  const w = await getWatch(id);
  if (!w || w.status !== "waiting") {
    timers.delete(id);
    return;
  }
  await exec(`UPDATE purchase_watches SET checks = checks + 1 WHERE id = ?`, [id]);

  let found: CardPurchase | undefined;
  let error: string | null = null;
  try {
    found = await pick(w.tallyId, w.requestedAt, await cardPurchases());
  } catch (e) {
    error = e instanceof Error ? e.message : String(e);
    await exec(`UPDATE purchase_watches SET error = ? WHERE id = ?`, [error.slice(0, 255), id]);
  }

  if (found) {
    const entryId = await addFromCard(w.tallyId, found);
    await finish(id, "added", { entryId });
    await pushToPhones({
      kind: "purchase-added",
      tallyId: w.tallyId,
      title: `${await tallyName(w.tallyId)} · ₪${found.amount.toLocaleString("en-US")}`,
      body: `Added: ${found.description}`,
    });
    return;
  }

  if (Date.now() >= w.until) {
    // Time is up. If every look failed, say why; otherwise nothing new reached the card.
    await finish(id, error ? "failed" : "expired", error ? { error } : {});
    await pushToPhones({
      kind: "purchase-missing",
      tallyId: w.tallyId,
      title: await tallyName(w.tallyId),
      body: error
        ? `Could not check the card: ${error}`
        : "No new card purchase reached Cal in 30 minutes. Cash or Bit never does — add it by hand.",
    });
    return;
  }
  schedule(id, CHECK_EVERY_MS);
}

/**
 * A press of the button. Starts looking at the card at once and answers immediately
 * with the new wait; the phone follows it with GET. A second press while one is
 * waiting returns that wait rather than starting another.
 */
export async function requestLastPurchase(tallyId: string): Promise<Watch> {
  if (!(await q(`SELECT 1 FROM tallies WHERE id = ?`, [tallyId])).length) throw new Error("no such tally");

  const waiting = await q<{ id: string }>(
    `SELECT id FROM purchase_watches WHERE tally_id = ? AND status = 'waiting' ORDER BY requested_at DESC LIMIT 1`,
    [tallyId],
  );
  if (waiting[0]) return (await getWatch(waiting[0].id))!;

  const id = newId();
  await exec(
    `INSERT INTO purchase_watches (id, tally_id, requested_at, status) VALUES (?, ?, ?, 'waiting')`,
    [id, tallyId, sqlTime(Date.now())],
  );
  // The first look runs in the background too: it takes ~15 s (longer when Cal is slow),
  // more than a phone on a bus should hold one request open for. The phone asks for the
  // watch with GET and shows "looking" while `checks` is 0.
  schedule(id, 0);
  return (await getWatch(id))!;
}

export async function cancelWatch(tallyId: string): Promise<void> {
  const rows = await q<{ id: string }>(
    `SELECT id FROM purchase_watches WHERE tally_id = ? AND status = 'waiting'`,
    [tallyId],
  );
  for (const r of rows) {
    clearTimeout(timers.get(r.id));
    await finish(r.id, "cancelled");
  }
}

/** After a server restart, pick every unfinished wait back up. Called from instrumentation.ts. */
export async function resumeWatches(): Promise<void> {
  const rows = await q<{ id: string }>(`SELECT id FROM purchase_watches WHERE status = 'waiting'`);
  for (const r of rows) if (!timers.has(r.id)) schedule(r.id, 5_000);
}
