// Workspace offboarding (#926) in a real browser against a real API: an admin exports the whole workspace from
// Settings → Data & retention and downloads the zip, deletes the workspace from Settings → General, and once the
// grace period is over the purge leaves nothing but a tombstone and a deletion certificate.
//
// Needs `npm run dev` (or its two halves) with a short purge interval, so the run need not wait fifteen minutes:
//
//   RETENTION_WORKSPACE_PURGE_INTERVAL=PT20S npm run dev
//   API_LOG=<the API's console log> node e2e/spa/workspace-offboarding.mjs
//
// The grace period is ended with SQL as the container's superuser, above row-level security: nobody can make a
// deletion thirty days old through the API.

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = process.env.API_LOG ?? join(RUN_DIR, "api.log");
const WEB = process.env.WEB ?? "http://localhost:5173";
const API = process.env.API ?? "http://localhost:8080/api/v1";
const PG_CONTAINER = process.env.PG_CONTAINER ?? "lm-dev-pg";
const DOCUMENTS = process.env.DOCUMENTS ?? join(HERE, "..", "..", "apps", "api", ".data", "documents");
const PASSWORD = "Passw0rd123";
const DOMAIN = "nextwebspark.com";
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;

/** Tables kept by id after a purge (`WorkspacePurgeTables.RETAINED`). */
const RETAINED = new Set([
  "app_lm_audit_event", "app_lm_credit_entry", "app_lm_credit_grant", "app_lm_credit_hold", "app_lm_credit_balance",
  "app_lm_credit_threshold_crossing", "app_lm_usage_event", "app_lm_workspace_subscription", "app_lm_billing_customer",
  "app_lm_billing_notice", "app_lm_workspace_purge_certificate", "app_lm_refresh_token",
]);

mkdirSync(SHOTS, { recursive: true });
mkdirSync(RUN_DIR, { recursive: true });

let passed = 0;
let failed = 0;
const record = (id, result, detail) => appendFileSync(join(RUN_DIR, "cases.tsv"), `${id}\t${result}\t${detail}\n`);
const pass = (id, what) => { passed++; console.log(`  \x1b[32mPASS\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "PASS", what); };
const fail = (id, what, why) => { failed++; console.log(`  \x1b[31mFAIL\x1b[0m ${id.padEnd(7)} ${what}\n         \x1b[2m${why}\x1b[0m`); record(id, "FAIL", `${what} -- ${why}`); };
const check = (id, what, expected, actual) =>
  expected === actual ? pass(id, what) : fail(id, what, `expected [${expected}] got [${actual}]`);
const step = async (id, what, body) => {
  try { await body(); } catch (error) { fail(id, what, String(error?.message ?? error).split("\n")[0]); }
};
const until = async (probe, timeoutMs, everyMs = 1000) => {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await probe()) return true;
    await new Promise((resolve) => setTimeout(resolve, everyMs));
  }
  return false;
};

const sql = (query) =>
  execFileSync("docker", ["exec", PG_CONTAINER, "psql", "-U", "postgres", "-d", "lightmove", "-Atc", query]).toString().trim();

function verifyTokenFor(address) {
  let mine = false;
  let found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(/\/auth\/verify\?token=([A-Za-z0-9_%.~-]+)/);
      if (match) found = match[1];
    }
  }
  return found;
}

const api = async (path, { method = "GET", body, token, form } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(form ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

// --- the seed -------------------------------------------------------------------------

const EMAIL = `lm-e2e-offboarding-${STAMP}@${DOMAIN}`;
const NAME = `Offboarded SPA ${STAMP}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Omar Offboard", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
await api("/auth/verify", { method: "POST", body: { token: verifyTokenFor(EMAIL) } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "COMPANY", name: NAME } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const WORKSPACE = sql(`SELECT id FROM app_lm_workspace WHERE name = '${NAME}'`);
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Finance ${STAMP}` } })).body.id;
const projectId = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Group Treasurer" } })).body.id;
const filed = (await api(`/projects/${projectId}/candidates`, {
  method: "POST", token,
  body: { fullName: "Huda Karim", linkedinUrl: `https://www.linkedin.com/in/huda-karim-${STAMP}/`, emails: [{ value: `huda-${STAMP}@example.com` }] },
})).body;
await api(`/candidates/${filed.personId}/notes`, { method: "POST", token, body: { kind: "call", body: "Would move for a group role" } });
const upload = new FormData();
upload.append("file", new Blob(["%PDF-1.7\nHuda"], { type: "application/pdf" }), "Huda_Karim_CV.pdf");
const uploaded = await api(`/candidates/${filed.personId}/documents`, { method: "POST", token, form: upload });
console.log(`  seeded workspace ${WORKSPACE}: position, executive, note, CV (${uploaded.status})`);

// --- the browser ----------------------------------------------------------------------

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
const page = await context.newPage();
const shot = (name) => page.screenshot({ path: join(SHOTS, `offboarding-${name}.png`), fullPage: true });

try {
  await step("WO1.1", "an admin exports the whole workspace and downloads the zip", async () => {
    await page.goto(`${WEB}/login`);
    await page.getByPlaceholder("you@firm.com").fill(EMAIL);
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/settings/data`);
    const card = page.getByRole("region", { name: "Export workspace data" });
    await card.getByRole("button", { name: "Start export" }).click();
    await card.getByRole("button", { name: "Download" }).waitFor({ timeout: 60000 });
    await shot("export-ready");
    check("WO1.1", "the ready card names the zip", true,
      (await card.textContent()).includes(`offboarded-spa-${STAMP}-export-`));
    const [download] = await Promise.all([page.waitForEvent("download"), card.getByRole("button", { name: "Download" }).click()]);
    const zip = readFileSync(await download.path());
    check("WO1.2", "the download is a zip", "PK", zip.subarray(0, 2).toString("latin1"));
    check("WO1.3", "the zip carries the executive's CV", true, zip.includes(Buffer.from("Huda_Karim_CV.pdf")));
    check("WO1.4", "the export and the download are audited", "2", sql(`SELECT count(*) FROM app_lm_audit_event
      WHERE workspace_id = '${WORKSPACE}' AND event_type IN ('WORKSPACE_EXPORT_STARTED', 'WORKSPACE_EXPORT_DOWNLOADED')`));
  });

  await step("WO2.1", "deleting asks for the name, says what happens when, and ends access", async () => {
    await page.goto(`${WEB}/settings/general`);
    await page.getByRole("button", { name: "Delete…" }).click();
    const dialog = page.getByRole("dialog", { name: `Delete ${NAME}?` });
    await dialog.waitFor();
    check("WO2.1", "the dialog states the grace period", true, (await dialog.textContent()).includes("30d"));
    check("WO2.2", "the dialog offers the export first", "/settings/data",
      await dialog.getByRole("link", { name: "Export data" }).getAttribute("href"));
    await page.waitForTimeout(400); // the dialog's fade-in
    await shot("delete-dialog");
    await dialog.getByLabel("Workspace name").fill(NAME.toLowerCase());
    await dialog.getByRole("button", { name: "Delete workspace" }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/settings"), { timeout: 20000 });
    check("WO2.3", "the workspace is deleted with its grace period", "DELETED|30",
      sql(`SELECT status || '|' || extract(day from purge_after - deleted_at) FROM app_lm_workspace WHERE id = '${WORKSPACE}'`));
    check("WO2.4", "every membership ended with it", "t",
      sql(`SELECT bool_and(status = 'REMOVED' AND ended_with_workspace) FROM app_lm_workspace_member WHERE workspace_id = '${WORKSPACE}'`));
  });

  await step("WO3.1", "past the grace period the purge leaves a tombstone and a certificate", async () => {
    check("WO3.0", "the files are there before the purge", true, existsSync(join(DOCUMENTS, "ws", WORKSPACE)));
    sql(`UPDATE app_lm_workspace SET purge_after = now() - interval '1 minute' WHERE id = '${WORKSPACE}'`);
    const purged = await until(() => sql(`SELECT purged_at IS NOT NULL FROM app_lm_workspace WHERE id = '${WORKSPACE}'`) === "t", 90000, 2000);
    check("WO3.1", "the purge ran", true, purged);
    const tables = sql(`SELECT c.relname FROM pg_class c WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace
      AND c.relname LIKE 'app\\_lm\\_%' AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = c.oid
      AND a.attname = 'workspace_id' AND NOT a.attisdropped)`).split("\n").filter((table) => !RETAINED.has(table));
    const left = tables.filter((table) => sql(`SELECT count(*) FROM ${table} WHERE workspace_id = '${WORKSPACE}'`) !== "0");
    check("WO3.2", `no row of the workspace is left in ${tables.length} tables`, "", left.join(", "));
    check("WO3.3", "nothing is left under its folder in the store", false, existsSync(join(DOCUMENTS, "ws", WORKSPACE)));
    check("WO3.4", "the row is a tombstone", `Deleted workspace|deleted-${WORKSPACE}|deleted.invalid|`,
      sql(`SELECT name || '|' || slug || '|' || email_domain || '|' || coalesce(purge_notice_email, '') FROM app_lm_workspace WHERE id = '${WORKSPACE}'`));
    const [people, hashChecks] = sql(`SELECT row_counts ->> 'app_lm_person', body_sha256 = encode(sha256(convert_to(body, 'UTF8')), 'hex')
      FROM app_lm_workspace_purge_certificate WHERE workspace_id = '${WORKSPACE}'`).split("|");
    check("WO3.5", "the certificate counts the executive", "1", people);
    check("WO3.6", "the certificate's hash is its body's", "t", hashChecks);
    check("WO3.7", "the certificate names no one", "0",
      sql(`SELECT count(*) FROM app_lm_workspace_purge_certificate WHERE workspace_id = '${WORKSPACE}' AND (body ILIKE '%Huda%' OR body ILIKE '%Offboard%')`));
    check("WO3.8", "the certificate was emailed to whoever deleted it", true,
      readFileSync(API_LOG, "utf8").includes(`${NAME} has been permanently deleted`));
  });
} finally {
  await browser.close();
}

console.log(`\n  ${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
