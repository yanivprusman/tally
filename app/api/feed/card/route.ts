import { guarded } from "@/lib/auth";
import { collectCardPurchases } from "@/lib/cardCollect";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * One pass of card collection (lib/cardCollect.ts), woken by tally-card-collect.timer.
 * Guarded like every route: an open trigger would let anyone on the LAN make tazrim
 * log in to Cal over and over, which is how a card account gets locked.
 */
export const POST = guarded(async () => Response.json({ ok: true, ...(await collectCardPurchases()) }));
