import "server-only";
import { q, exec } from "./db";

/**
 * Every query in one place, and every one of them filtered on `deleted_at IS
 * NULL`. Nothing in this file issues a DELETE: reset and delete mark rows, so a
 * tally that vanishes from the app is still on disk and one call away from
 * coming back. That is the whole reason the records moved off the phone.
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

const ms = (s: string) => new Date(s.replace(" ", "T") + "Z").getTime();

export async function listTallies(): Promise<Tally[]> {
  const tallies = await q<{
    id: string; name: string; currency: string; accent: number; created_at: string;
  }>(`SELECT id, name, currency, accent, created_at
        FROM tallies WHERE deleted_at IS NULL ORDER BY created_at`);
  if (tallies.length === 0) return [];

  const rows = await q<{
    id: string; tally_id: string; direction: Direction; amount: number;
    note: string; category: string; at: string;
  }>(`SELECT id, tally_id, direction, amount, note, category, at
        FROM entries
       WHERE deleted_at IS NULL AND tally_id IN (${tallies.map(() => "?").join(",")})
       ORDER BY at`, tallies.map((t) => t.id));

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

export async function createTally(t: {
  id: string; name: string; currency: string; accent: number;
}) {
  await exec(
    `INSERT INTO tallies (id, name, currency, accent) VALUES (?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE name = VALUES(name), currency = VALUES(currency),
                               accent = VALUES(accent), deleted_at = NULL`,
    [t.id, t.name.slice(0, 64), t.currency, t.accent],
  );
}

export async function updateTally(id: string, t: {
  name: string; currency: string; accent: number;
}) {
  await exec(
    `UPDATE tallies SET name = ?, currency = ?, accent = ? WHERE id = ? AND deleted_at IS NULL`,
    [t.name.slice(0, 64), t.currency, t.accent, id],
  );
}

export async function deleteTally(id: string) {
  await exec(`UPDATE tallies SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL`, [id]);
}

/**
 * Clears a tally without destroying it. The batch id groups exactly the entries
 * this reset cleared, so undoing it cannot also resurrect an entry the user had
 * deliberately deleted one at a time beforehand.
 */
export async function resetTally(id: string): Promise<string> {
  const batch = `r${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
  await exec(
    `UPDATE entries SET deleted_at = NOW(), reset_batch = ?
      WHERE tally_id = ? AND deleted_at IS NULL`,
    [batch, id],
  );
  return batch;
}

export async function addEntry(tallyId: string, e: {
  id: string; direction: Direction; amount: number; note: string; category: string;
}) {
  await exec(
    `INSERT INTO entries (id, tally_id, direction, amount, note, category)
          VALUES (?, ?, ?, ?, ?, ?)`,
    [e.id, tallyId, e.direction, e.amount, e.note.slice(0, 255), e.category],
  );
}

export async function updateEntry(id: string, e: {
  direction: Direction; amount: number; note: string; category: string;
}) {
  await exec(
    `UPDATE entries SET direction = ?, amount = ?, note = ?, category = ?
      WHERE id = ? AND deleted_at IS NULL`,
    [e.direction, e.amount, e.note.slice(0, 255), e.category, id],
  );
}

export async function deleteEntry(id: string) {
  await exec(`UPDATE entries SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL`, [id]);
}

/** Undo. Nothing was destroyed, so this is only ever clearing a mark. */
export async function restore(kind: string, id: string) {
  if (kind === "tally") {
    await exec(`UPDATE tallies SET deleted_at = NULL WHERE id = ?`, [id]);
  } else if (kind === "entry") {
    await exec(`UPDATE entries SET deleted_at = NULL WHERE id = ?`, [id]);
  } else if (kind === "reset") {
    await exec(
      `UPDATE entries SET deleted_at = NULL, reset_batch = NULL WHERE reset_batch = ?`,
      [id],
    );
  } else {
    throw new Error(`unknown restore kind: ${kind}`);
  }
}
