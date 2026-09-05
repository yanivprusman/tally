import { guarded } from "@/lib/auth";
import { listTallies, createTally } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Everything the app shows, in one round trip — a tally is small and the phone
 *  is often on a slow link, so paging would cost more than it saves. */
export const GET = guarded(async () => Response.json({ ok: true, tallies: await listTallies() }));

export const POST = guarded(async (request) => {
  const b = await request.json();
  const id = String(b.id ?? "").trim();
  const name = String(b.name ?? "").trim();
  if (!id || !name) return Response.json({ ok: false, error: "id and name are required" }, { status: 400 });
  await createTally({
    id,
    name,
    currency: String(b.currency ?? "₪"),
    accent: Number(b.accent ?? 0),
  });
  return Response.json({ ok: true });
});
