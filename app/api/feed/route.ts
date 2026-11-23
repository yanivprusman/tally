import { guarded } from "@/lib/auth";
import { putFeed, removeFeed, VAT_MODES, type VatMode } from "@/lib/feed";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Other apps write here; see lib/feed.ts. Same bearer token as every other route.
 *
 * PUT { ref, at, direction, amount, vat, note, category }
 *   → { ok, tallies: [ids that hold it] }   (empty when no tally is open — not an error)
 * DELETE ?ref=… → { ok, removed }
 */
export const PUT = guarded(async (request) => {
  const b = await request.json();
  const ref = String(b.ref ?? "").trim();
  const at = Number(b.at);
  const amount = Number(b.amount);
  const vat = String(b.vat ?? "") as VatMode;
  if (!ref) return Response.json({ ok: false, error: "ref is required" }, { status: 400 });
  if (!(at > 0)) return Response.json({ ok: false, error: "at must be epoch ms" }, { status: 400 });
  if (!(amount > 0)) return Response.json({ ok: false, error: "amount must be positive" }, { status: 400 });
  if (b.direction !== "IN" && b.direction !== "OUT")
    return Response.json({ ok: false, error: "direction must be IN or OUT" }, { status: 400 });
  if (!VAT_MODES.includes(vat))
    return Response.json({ ok: false, error: `vat must be one of ${VAT_MODES.join(", ")}` }, { status: 400 });

  const tallies = await putFeed({
    ref, at, amount, vat, direction: b.direction,
    note: String(b.note ?? ""), category: String(b.category ?? "other"),
  });
  return Response.json({ ok: true, tallies });
});

export const DELETE = guarded(async (request) => {
  const ref = new URL(request.url).searchParams.get("ref")?.trim() ?? "";
  if (!ref) return Response.json({ ok: false, error: "ref is required" }, { status: 400 });
  return Response.json({ ok: true, removed: await removeFeed(ref) });
});
