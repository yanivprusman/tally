import { guarded } from "@/lib/auth";
import { resetTally } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Empties the tally and keeps it. The entries are deleted; Undo POSTs them back. */
export const POST = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await resetTally(id);
  return Response.json({ ok: true });
});
