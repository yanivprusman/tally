import { guarded } from "@/lib/auth";
import { restore } from "@/lib/tallies";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** Undo. Every destructive route hands back the {kind, id} to send here. */
export const POST = guarded(async (request) => {
  const b = await request.json();
  const kind = String(b.kind ?? "");
  const id = String(b.id ?? "");
  if (!kind || !id) return Response.json({ ok: false, error: "kind and id are required" }, { status: 400 });
  await restore(kind, id);
  return Response.json({ ok: true });
});
