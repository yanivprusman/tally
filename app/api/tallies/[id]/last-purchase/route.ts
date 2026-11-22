import { guarded } from "@/lib/auth";
import { cancelWatch, latestWatch, requestLastPurchase } from "@/lib/lastPurchase";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Press the button: look at the card now, and keep waiting for the purchase if it is not there yet. */
export const POST = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  return Response.json({ ok: true, watch: await requestLastPurchase(id) });
});

/** How the newest press on this tally stands — the phone asks while it shows "waiting". */
export const GET = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  return Response.json({ ok: true, watch: await latestWatch(id) });
});

/** Stop waiting. */
export const DELETE = guarded(async (_request, ctx) => {
  const { id } = await ctx.params;
  await cancelWatch(id);
  return Response.json({ ok: true, watch: await latestWatch(id) });
});
