/** A server restart must not forget a press of "add my last purchase" that is still waiting. */
export async function register() {
  // This exact shape lets the bundler drop the import from the edge build, where mysql2 cannot load.
  if (process.env.NEXT_RUNTIME === "nodejs") {
    const { resumeWatches } = await import("./lib/lastPurchase");
    await resumeWatches().catch((e) => console.error("[last-purchase] could not resume waits:", e));
  }
}
