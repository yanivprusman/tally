import { guarded } from "@/lib/auth";
import { addEntry, type Direction } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const POST = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();
  const entryId = String(b.id ?? "").trim();
  const direction = b.direction === "IN" ? "IN" : "OUT";
  const amount = Number(b.amount ?? 0);
  if (!entryId) return Response.json({ ok: false, error: "id is required" }, { status: 400 });
  if (!Number.isFinite(amount) || amount <= 0)
    return Response.json({ ok: false, error: "amount must be a positive number of agorot" }, { status: 400 });
  await addEntry(id, {
    id: entryId,
    direction: direction as Direction,
    amount: Math.round(amount),
    note: String(b.note ?? ""),
    category: String(b.category ?? "other"),
  });
  return Response.json({ ok: true });
});
