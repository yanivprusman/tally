import "server-only";
import { q, exec, pool } from "./db";

/**
 * Every query in one place.
 *
 * A delete here is a real DELETE. The reason the records moved off the phone was
 * so that *accidents* — a wipe, a reinstall, a stray adb command — cannot destroy
 * them; a delete the user chose is not an accident, and a row that quietly stays
 * behind a flag is not a delete. Undo works because the client still holds what
 * it removed for as long as the snackbar is up, and puts it back with `saveTally`.
 */

export type Direction = "IN" | "OUT";

export type Entry = {
  id: string;
  direction: Direction;
  amount: number;
  note: string;
  category: string;
  at: number;
  /** VAT sits inside `amount`. The amount itself is always what was paid. */
  vatIncluded: boolean;
  /** Set when another app put this entry here ("countdown:<tickId>"), see lib/feed.ts. */
  sourceRef?: string | null;
};

export type Tally = {
  id: string;
  name: string;
  currency: string;
  accent: number;
  /** Show this tally with VAT taken back out of the entries that carry it. */
  exVat: boolean;
  /** Collecting: money other apps record lands here by itself (lib/feed.ts) until stopped. */
  open: boolean;
  createdAt: number;
  entries: Entry[];
};

// Times are UTC end to end. MySQL's CURRENT_TIMESTAMP writes in the *server's*
// zone (IDT here), which read back as UTC put every entry three hours in the
// future — so nothing relies on a column default: the client owns the clock and
// every write passes an explicit UTC string.
const ms = (s: string) => new Date(s.replace(" ", "T") + "Z").getTime();
const sqlTime = (epochMs: number) => new Date(epochMs).toISOString().slice(0, 19).replace("T", " ");

export async function listTallies(): Promise<Tally[]> {
  const tallies = await q<{
    id: string; name: string; currency: string; accent: number; ex_vat: number; is_open: number; created_at: string;
  }>(`SELECT id, name, currency, accent, ex_vat, is_open, created_at FROM tallies ORDER BY created_at`);
  if (tallies.length === 0) return [];

  const rows = await q<{
    id: string; tally_id: string; direction: Direction; amount: number;
    note: string; category: string; at: string; vat_included: number; source_ref: string | null;
  }>(`SELECT id, tally_id, direction, amount, note, category, at, vat_included, source_ref
        FROM entries WHERE tally_id IN (${tallies.map(() => "?").join(",")}) ORDER BY at`,
    tallies.map((t) => t.id));

  const byTally = new Map<string, Entry[]>();
  for (const r of rows) {
    const list = byTally.get(r.tally_id) ?? [];
    list.push({
      id: r.id,
      direction: r.direction,
      amount: Number(r.amount),
      note: r.note,
      category: r.category,
      at: ms(r.at),
      vatIncluded: Boolean(r.vat_included),
      sourceRef: r.source_ref,
    });
    byTally.set(r.tally_id, list);
  }

  return tallies.map((t) => ({
    id: t.id,
    name: t.name,
    currency: t.currency,
    accent: Number(t.accent),
    exVat: Boolean(t.ex_vat),
    open: Boolean(t.is_open),
    createdAt: ms(t.created_at),
    entries: byTally.get(t.id) ?? [],
  }));
}

/**
 * Creates a tally, and — when `entries` is given — puts them back with their
 * original ids and timestamps. That second form is what Undo uses, so an undone
 * delete restores the tally exactly as it was rather than as a fresh one dated
 * today. Idempotent, so a retried undo is harmless.
 *
 * A new tally is open (collecting) unless the caller says otherwise — starting a tally
 * is starting to count. Undo sends the `open` it had, so it comes back as it was.
 */
export async function saveTally(
  t: { id: string; name: string; currency: string; accent: number; exVat: boolean; open?: boolean; createdAt: number },
  entries?: Entry[],
) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    await conn.query(
      `INSERT INTO tallies (id, name, currency, accent, ex_vat, is_open, opened_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE name = VALUES(name), currency = VALUES(currency),
                               accent = VALUES(accent), ex_vat = VALUES(ex_vat)`,
      [t.id, t.name.slice(0, 64), t.currency, t.accent, t.exVat ? 1 : 0, t.open === false ? 0 : 1,
       t.open === false ? null : sqlTime(t.createdAt), sqlTime(t.createdAt)],
    );
    for (const e of entries ?? []) {
      await conn.query(
        `INSERT INTO entries (id, tally_id, direction, amount, note, category, at, vat_included, source_ref)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE direction = VALUES(direction), amount = VALUES(amount),
                                 note = VALUES(note), category = VALUES(category), at = VALUES(at),
                                 vat_included = VALUES(vat_included), source_ref = VALUES(source_ref)`,
        [e.id, t.id, e.direction, Math.round(e.amount), String(e.note).slice(0, 255),
         e.category, sqlTime(e.at), e.vatIncluded ? 1 : 0, e.sourceRef ? String(e.sourceRef).slice(0, 200) : null],
      );
    }
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}

export async function updateTally(id: string, t: {
  name: string; currency: string; accent: number; exVat: boolean;
}) {
  await exec(`UPDATE tallies SET name = ?, currency = ?, accent = ?, ex_vat = ? WHERE id = ?`,
    [t.name.slice(0, 64), t.currency, t.accent, t.exVat ? 1 : 0, id]);
}

/** Start or stop collecting. Stopping keeps everything already collected. */
export async function setTallyOpen(id: string, open: boolean) {
  // Opening (again) restarts the clock card purchases are collected from: what was
  // bought while the tally was stopped is not collected afterwards.
  if (open) await exec(`UPDATE tallies SET is_open = 1, opened_at = ? WHERE id = ? AND is_open = 0`,
    [sqlTime(Date.now()), id]);
  else await exec(`UPDATE tallies SET is_open = 0 WHERE id = ?`, [id]);
}

/** Gone. The foreign key takes its entries with it. */
export async function deleteTally(id: string) {
  await exec(`DELETE FROM tallies WHERE id = ?`, [id]);
}

/** Empties a tally and keeps the tally itself. */
export async function resetTally(id: string) {
  await exec(`DELETE FROM entries WHERE tally_id = ?`, [id]);
}

export async function addEntry(tallyId: string, e: {
  id: string; direction: Direction; amount: number; note: string; category: string;
  at: number; vatIncluded: boolean;
}) {
  await exec(
    `INSERT INTO entries (id, tally_id, direction, amount, note, category, at, vat_included)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [e.id, tallyId, e.direction, e.amount, e.note.slice(0, 255), e.category, sqlTime(e.at),
     e.vatIncluded ? 1 : 0],
  );
}

export async function updateEntry(id: string, e: {
  direction: Direction; amount: number; note: string; category: string; vatIncluded: boolean;
}) {
  await exec(
    `UPDATE entries SET direction = ?, amount = ?, note = ?, category = ?, vat_included = ?
      WHERE id = ?`,
    [e.direction, e.amount, e.note.slice(0, 255), e.category, e.vatIncluded ? 1 : 0, id],
  );
}

export async function deleteEntry(id: string) {
  await exec(`DELETE FROM entries WHERE id = ?`, [id]);
}
