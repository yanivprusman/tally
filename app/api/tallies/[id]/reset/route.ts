import { guarded } from "@/lib/auth";
import { resetTally } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Empties the tally and keeps it. Returns the batch so one reset can be undone
 *  as a unit, without resurrecting entries deleted individually beforehand. */
export const POST = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  const batch = await resetTally(id);
  return Response.json({ ok: true, undo: { kind: "reset", id: batch } });
});
