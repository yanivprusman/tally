import { guarded } from "@/lib/auth";
import { updateEntry, deleteEntry, type Direction } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PATCH = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();
  const amount = Number(b.amount ?? 0);
  if (!Number.isFinite(amount) || amount <= 0)
    return Response.json({ ok: false, error: "amount must be a positive number of agorot" }, { status: 400 });
  await updateEntry(id, {
    direction: (b.direction === "IN" ? "IN" : "OUT") as Direction,
    amount: Math.round(amount),
    note: String(b.note ?? ""),
    category: String(b.category ?? "other"),
  });
  return Response.json({ ok: true });
});

export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await deleteEntry(id);
  return Response.json({ ok: true, undo: { kind: "entry", id } });
});
