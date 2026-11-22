import { guarded } from "@/lib/auth";
import { registerEndpoint } from "@/lib/push";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/** The phone's UnifiedPush endpoint, sent on every app start (see lib/push.ts). */
export const POST = guarded(async (request) => {
  const b = await request.json();
  const endpoint = String(b.endpoint ?? "").trim();
  if (!/^https?:\/\//.test(endpoint))
    return Response.json({ ok: false, error: "endpoint must be a URL" }, { status: 400 });
  await registerEndpoint(endpoint);
  return Response.json({ ok: true });
});
