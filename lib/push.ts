import "server-only";
import { q, exec } from "@/lib/db";

/**
 * Waking the phone: UnifiedPush through the suite's own ntfy server
 * (/opt/automateLinux/services/ntfy/README.md) — never Firebase. The phone registers an
 * endpoint URL (POST /api/push/register); a POST to it reaches the tally app even when
 * it is closed, and the app turns the JSON body into a notification.
 */

export type PushMessage = {
  kind: "purchase-added" | "purchase-missing";
  tallyId: string;
  title: string;
  body: string;
};

export async function registerEndpoint(endpoint: string) {
  await exec(
    `INSERT INTO push_endpoints (endpoint, registered_at) VALUES (?, ?)
       ON DUPLICATE KEY UPDATE registered_at = VALUES(registered_at)`,
    [endpoint, new Date().toISOString().slice(0, 19).replace("T", " ")],
  );
}

/** Best effort per phone: a push that does not arrive costs a notification, not a record,
 *  and the app shows the outcome the next time it opens that tally. Failures are logged. */
export async function pushToPhones(m: PushMessage) {
  const rows = await q<{ endpoint: string }>(`SELECT endpoint FROM push_endpoints`);
  await Promise.all(
    rows.map(async ({ endpoint }) => {
      try {
        const res = await fetch(endpoint, {
          method: "POST",
          body: JSON.stringify(m),
          signal: AbortSignal.timeout(10_000),
        });
        // 404/410: the phone dropped this registration (reinstall); forget it.
        if (res.status === 404 || res.status === 410)
          await exec(`DELETE FROM push_endpoints WHERE endpoint = ?`, [endpoint]);
        else if (!res.ok) console.error(`[push] ${endpoint} answered HTTP ${res.status}`);
      } catch (e) {
        console.error(`[push] ${endpoint}: ${e instanceof Error ? e.message : e}`);
      }
    }),
  );
}
