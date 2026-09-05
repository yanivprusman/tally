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
};

export type Tally = {
  id: string;
  name: string;
  currency: string;
  accent: number;
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
    id: string; name: string; currency: string; accent: number; created_at: string;
  }>(`SELECT id, name, currency, accent, created_at FROM tallies ORDER BY created_at`);
  if (tallies.length === 0) return [];

  const rows = await q<{
    id: string; tally_id: string; direction: Direction; amount: number;
    note: string; category: string; at: string;
  }>(`SELECT id, tally_id, direction, amount, note, category, at
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
    });
    byTally.set(r.tally_id, list);
  }

  return tallies.map((t) => ({
    id: t.id,
    name: t.name,
    currency: t.currency,
    accent: Number(t.accent),
    createdAt: ms(t.created_at),
    entries: byTally.get(t.id) ?? [],
  }));
}

/**
 * Creates a tally, and — when `entries` is given — puts them back with their
 * original ids and timestamps. That second form is what Undo uses, so an undone
 * delete restores the tally exactly as it was rather than as a fresh one dated
 * today. Idempotent, so a retried undo is harmless.
 */
export async function saveTally(
  t: { id: string; name: string; currency: string; accent: number; createdAt: number },
  entries?: Entry[],
) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    await conn.query(
      `INSERT INTO tallies (id, name, currency, accent, created_at)
            VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE name = VALUES(name), currency = VALUES(currency),
                               accent = VALUES(accent)`,
      [t.id, t.name.slice(0, 64), t.currency, t.accent, sqlTime(t.createdAt)],
    );
    for (const e of entries ?? []) {
      await conn.query(
        `INSERT INTO entries (id, tally_id, direction, amount, note, category, at)
              VALUES (?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE direction = VALUES(direction), amount = VALUES(amount),
                                 note = VALUES(note), category = VALUES(category), at = VALUES(at)`,
        [e.id, t.id, e.direction, Math.round(e.amount), String(e.note).slice(0, 255),
         e.category, sqlTime(e.at)],
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
  name: string; currency: string; accent: number;
}) {
  await exec(`UPDATE tallies SET name = ?, currency = ?, accent = ? WHERE id = ?`,
    [t.name.slice(0, 64), t.currency, t.accent, id]);
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
  id: string; direction: Direction; amount: number; note: string; category: string; at: number;
}) {
  await exec(
    `INSERT INTO entries (id, tally_id, direction, amount, note, category, at)
          VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [e.id, tallyId, e.direction, e.amount, e.note.slice(0, 255), e.category, sqlTime(e.at)],
  );
}

export async function updateEntry(id: string, e: {
  direction: Direction; amount: number; note: string; category: string;
}) {
  await exec(
    `UPDATE entries SET direction = ?, amount = ?, note = ?, category = ? WHERE id = ?`,
    [e.direction, e.amount, e.note.slice(0, 255), e.category, id],
  );
}

export async function deleteEntry(id: string) {
  await exec(`DELETE FROM entries WHERE id = ?`, [id]);
}
