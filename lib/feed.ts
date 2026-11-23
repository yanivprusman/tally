import "server-only";
import { randomBytes } from "node:crypto";
import { q, exec } from "./db";

/**
 * Money another app recorded, landing in the tallies that are open.
 *
 * The first source is countdown: a counted work day that says what it paid ("600 plus
 * VAT") is income, so it goes to tazrim (the forecast) and, through here, to every
 * tally that is collecting at the time. The source owns the entry, keyed by `ref`
 * ("countdown:<tickId>"), exactly the way it owns its tazrim row:
 *
 *  - The first time a ref arrives it is added to every OPEN tally.
 *  - After that, saving it again (an edit, an undo) updates the entries it already
 *    has — in whatever tallies hold it, open or stopped — and adds it nowhere new.
 *    Editing last week's day must correct last week's tally, not drop a copy into
 *    this week's.
 *  - Removing it at the source removes it from every tally.
 *
 * Amounts follow the tally's own rule: `amount` is what is paid (gross), and
 * `vat_included` says VAT sits inside it, so the tally's ex-VAT view shows the net.
 * The 18% here is the same constant the phone uses (mobile …/data/Vat.kt).
 */

const VAT_PERCENT = 18;
export const VAT_MODES = ["plus", "included", "none"] as const;
export type VatMode = (typeof VAT_MODES)[number];

export type FeedItem = {
  ref: string;
  /** Epoch ms of when it happened. */
  at: number;
  direction: "IN" | "OUT";
  /** In shekels, as said: with `vat: "plus"` this is before VAT. */
  amount: number;
  vat: VatMode;
  note: string;
  category: string;
};

const sqlTime = (epochMs: number) => new Date(epochMs).toISOString().slice(0, 19).replace("T", " ");
const newId = () => `${Date.now().toString(36)}-${randomBytes(3).toString("hex")}`;

/** Minor units actually paid, and whether VAT sits inside them. */
export function paid(amount: number, vat: VatMode): { agorot: number; vatIncluded: boolean } {
  const shekels = vat === "plus" ? amount * (1 + VAT_PERCENT / 100) : amount;
  return { agorot: Math.round(shekels * 100), vatIncluded: vat !== "none" };
}

/** Puts (or rewrites) the item. Returns the tally ids that now hold it. */
export async function putFeed(item: FeedItem): Promise<string[]> {
  const { agorot, vatIncluded } = paid(item.amount, item.vat);
  const values = [item.direction, agorot, item.note.slice(0, 255), item.category.slice(0, 32),
    sqlTime(item.at), vatIncluded ? 1 : 0];

  const held = await q<{ tally_id: string }>(`SELECT tally_id FROM entries WHERE source_ref = ?`, [item.ref]);
  if (held.length > 0) {
    await exec(
      `UPDATE entries SET direction = ?, amount = ?, note = ?, category = ?, at = ?, vat_included = ?
        WHERE source_ref = ?`, [...values, item.ref]);
    return held.map((h) => h.tally_id);
  }

  const open = await q<{ id: string }>(`SELECT id FROM tallies WHERE is_open = 1`);
  for (const t of open) {
    // INSERT IGNORE on (tally_id, source_ref): two racing saves of the same ref add it once.
    await exec(
      `INSERT IGNORE INTO entries (id, tally_id, direction, amount, note, category, at, vat_included, source_ref)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`, [newId(), t.id, ...values, item.ref]);
  }
  return open.map((t) => t.id);
}

/** Removes the item from every tally. Removing one that is nowhere is fine. */
export async function removeFeed(ref: string): Promise<number> {
  const res = await exec(`DELETE FROM entries WHERE source_ref = ?`, [ref]);
  return res.affectedRows;
}
