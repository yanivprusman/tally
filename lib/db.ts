import mysql from "mysql2/promise";

// Single shared pool against the system MySQL (3306), where the `tally` database
// lives — the same arrangement as the sibling apps (veggieBox, lawSuits,
// govaBoard). Creds overridable via env so the same code runs wherever the app
// is hosted.
const globalForDb = globalThis as typeof globalThis & { _tallyPool?: mysql.Pool };

export const pool =
  globalForDb._tallyPool ??
  mysql.createPool({
    host: process.env.DB_HOST ?? "127.0.0.1",
    port: Number(process.env.DB_PORT ?? 3306),
    user: process.env.DB_USER ?? "tally",
    password: process.env.DB_PASSWORD ?? "tally123",
    database: process.env.DB_NAME ?? "tally",
    waitForConnections: true,
    connectionLimit: 5,
    charset: "utf8mb4",
    dateStrings: true,
  });

if (process.env.NODE_ENV !== "production") globalForDb._tallyPool = pool;

export async function q<T = Record<string, unknown>>(
  sql: string,
  params?: unknown[],
): Promise<T[]> {
  const [rows] = await pool.query(sql, params);
  return rows as T[];
}

export async function exec(sql: string, params?: unknown[]) {
  const [res] = await pool.query(sql, params);
  return res as mysql.ResultSetHeader;
}
