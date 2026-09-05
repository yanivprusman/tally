import "server-only";

/**
 * The one guard on this API.
 *
 * The service listens on 0.0.0.0 — the WireGuard overlay AND the home LAN — so
 * "only my devices can reach it" is not true enough to rely on, and these routes
 * are the only copy of the user's money records.
 *
 * A missing token in the environment REFUSES every request. An API that guards
 * nothing because its secret was never set is worse than one that is down: the
 * first looks like it is working.
 */
export function authorize(request: Request): string | null {
  const expected = process.env.TALLY_API_TOKEN ?? "";
  if (!expected) return "TALLY_API_TOKEN is not set on the server";

  const header = request.headers.get("authorization") ?? "";
  const given = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!given) return "missing bearer token";

  // Compare the full length always, so a wrong token cannot be narrowed down by
  // how fast it is rejected.
  if (given.length !== expected.length) return "bad token";
  let diff = 0;
  for (let i = 0; i < expected.length; i++)
    diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0 ? null : "bad token";
}

/** Wraps a handler so every route answers 401 the same way. */
export function guarded(
  handler: (request: Request, ctx: { params: Promise<Record<string, string>> }) => Promise<Response>,
) {
  return async (request: Request, ctx: { params: Promise<Record<string, string>> }) => {
    const denied = authorize(request);
    if (denied) return Response.json({ ok: false, error: denied }, { status: 401 });
    try {
      return await handler(request, ctx);
    } catch (e) {
      // The database being unreachable is the interesting failure here, and the
      // phone has to be able to say so in words rather than spin forever.
      return Response.json(
        { ok: false, error: e instanceof Error ? e.message : String(e) },
        { status: 500 },
      );
    }
  };
}
