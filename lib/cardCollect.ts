import "server-only";
import { randomBytes } from "node:crypto";
import { q, exec } from "@/lib/db";
import { pushToPhones } from "@/lib/push";
import { CATEGORY, cardPurchases, refOf, type CardPurchase } from "@/lib/lastPurchase";

/**
 * Card purchases flow into open tallies by themselves.
 *
 * A timer (deploy/tally-card-collect.timer) wakes POST /api/feed/card every 20 minutes
 * by day. With no tally open it returns at once and Cal is not touched — the login is
 * the costly, lockable part, so it happens only while something is collecting. With
 * one open, it asks tazrim for the card's recent purchases (tazrim holds the Cal login,
 * see lib/lastPurchase.ts) and adds to each open tally every purchase that
 *
 *  - was made after the tally was opened (`opened_at`),
 *  - is a new purchase, not payment 2+ of an installment plan, and
 *  - the tally does not have yet: not taken from the card before (same purchase time
 *    and amount — the description is left out because Cal may reword a pending row
 *    when it completes), and not typed in by hand (same amount within HAND_MS — a
 *    purchase is often typed in an hour after paying).
 *
 * Each addition wakes the phone with a notification that opens that tally.
 */

const HAND_MS = 12 * 3600_000;

const ms = (s: string) => new Date(s.replace(" ", "T") + "Z").getTime();
const sqlTime = (epochMs: number) => new Date(epochMs).toISOString().slice(0, 19).replace("T", " ");
const newId = () => Date.now().toString(36) + "-" + randomBytes(4).toString("hex").slice(0, 5);

export type CollectResult = { open: number; checked: boolean; added: { tally: string; amount: number; description: string }[] };

let running: Promise<CollectResult> | null = null;

/** One pass. Concurrent calls share the pass in flight. */
export function collectCardPurchases(): Promise<CollectResult> {
  running ??= pass().finally(() => { running = null; });
  return running;
}

async function pass(): Promise<CollectResult> {
  const open = await q<{ id: string; name: string; opened_at: string | null }>(
    `SELECT id, name, opened_at FROM tallies WHERE is_open = 1 AND opened_at IS NOT NULL`);
  if (open.length === 0) return { open: 0, checked: false, added: [] };

  const purchases = (await cardPurchases())
    .filter((p) => !(p.installment && p.installment.number > 1))
    .sort((a, b) => a.date.localeCompare(b.date));

  const added: CollectResult["added"] = [];
  for (const t of open) {
    const since = ms(t.opened_at!);
    const rows = await q<{ amount: number; at: string; card_ref: string | null; source_ref: string | null }>(
      `SELECT amount, at, card_ref, source_ref FROM entries WHERE tally_id = ? AND direction = 'OUT'`, [t.id]);

    for (const p of purchases) {
      const at = Date.parse(p.date);
      // A row dated ahead of now is a scheduled charge, not something bought.
      if (at < since || at > Date.now() + 3600_000) continue;
      const agorot = Math.round(p.amount * 100);
      const has = rows.some((r) => {
        if (Number(r.amount) !== agorot) return false;
        if (r.card_ref) return r.card_ref.split("|")[0] === p.date;
        return !r.source_ref && Math.abs(ms(r.at) - at) <= HAND_MS;
      });
      if (has) continue;

      await addPurchase(t.id, p);
      rows.push({ amount: agorot, at: sqlTime(at), card_ref: refOf(p), source_ref: null });
      added.push({ tally: t.name, amount: p.amount, description: p.description });
      await pushToPhones({
        kind: "purchase-collected",
        tallyId: t.id,
        title: `${t.name} · ₪${p.amount.toLocaleString("en-US")}`,
        body: `Added from the card: ${p.description}`,
      });
    }
  }
  return { open: open.length, checked: true, added };
}

async function addPurchase(tallyId: string, p: CardPurchase) {
  await exec(
    `INSERT INTO entries (id, tally_id, direction, amount, note, category, at, vat_included, card_ref)
          VALUES (?, ?, 'OUT', ?, ?, ?, ?, 0, ?)`,
    [newId(), tallyId, Math.round(p.amount * 100), p.description.slice(0, 255), CATEGORY[p.category] ?? "other",
     sqlTime(Date.parse(p.date)), refOf(p)],
  );
}
