import { guarded } from "@/lib/auth";
import { listTallies, saveTally, type Entry } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Everything the app shows, in one round trip — a tally is small and the phone
 *  is often on a slow link, so paging would cost more than it saves. */
export const GET = guarded(async () => Response.json({ ok: true, tallies: await listTallies() }));

/**
 * Create a tally — or put one back. Undo sends the whole tally including its
 * entries, so an undone delete restores it exactly as it was rather than as a
 * fresh one dated today.
 */
export const POST = guarded(async (request) => {
  const b = await request.json();
  const id = String(b.id ?? "").trim();
  const name = String(b.name ?? "").trim();
  if (!id || !name) return Response.json({ ok: false, error: "id and name are required" }, { status: 400 });

  const entries = Array.isArray(b.entries) ? (b.entries as Entry[]) : undefined;
  await saveTally(
    {
      id,
      name,
      currency: String(b.currency ?? "₪"),
      accent: Number(b.accent ?? 0),
      createdAt: Number(b.createdAt) || Date.now(),
    },
    entries,
  );
  return Response.json({ ok: true });
});
