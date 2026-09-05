import { guarded } from "@/lib/auth";
import { updateTally, deleteTally } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const PATCH = guarded(async (request, ctx) => {
  const { id } = await ctx.params;
  const b = await request.json();
  const name = String(b.name ?? "").trim();
  if (!name) return Response.json({ ok: false, error: "name is required" }, { status: 400 });
  await updateTally(id, {
    name,
    currency: String(b.currency ?? "₪"),
    accent: Number(b.accent ?? 0),
  });
  return Response.json({ ok: true });
});

/** Soft: the rows stay, so this is undoable for as long as anyone wants it to be. */
export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await deleteTally(id);
  return Response.json({ ok: true, undo: { kind: "tally", id } });
});
