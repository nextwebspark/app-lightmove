// Settings → Data & retention (#921 part 2, #920) in a real browser against a real API: the candidate retention
// period, the people due for deletion kept and deleted through the erasure path, and the suppression list.
//
// Needs `npm run dev` (or its two halves). The seed ages people with SQL as the container's superuser, above
// row-level security, since nobody can make a person two years old through the API.
//
//   API_LOG=<the API's console log> node e2e/spa/data-retention.mjs

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = process.env.API_LOG ?? join(RUN_DIR, "api.log");
const WEB = process.env.WEB ?? "http://localhost:5173";
const API = process.env.API ?? "http://localhost:8080/api/v1";
const PG_CONTAINER = process.env.PG_CONTAINER ?? "lm-dev-pg";
const PASSWORD = "Passw0rd123";
const DOMAIN = "nextwebspark.com";
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;

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

const sql = (query) =>
  execFileSync("docker", ["exec", PG_CONTAINER, "psql", "-U", "postgres", "-d", "lightmove", "-Atc", query]).toString().trim();

function linkFor(address, kind = "verify") {
  let mine = false;
  let found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(new RegExp(`http://localhost:5173/auth/${kind}\\?token=[A-Za-z0-9_%.~-]+`));
      if (match) found = match[0];
    }
  }
  return found;
}

const api = async (path, { method = "GET", body, token } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

// --- the seed -------------------------------------------------------------------------

const EMAIL = `lm-e2e-retention-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Rania Retention", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
await api("/auth/verify", { method: "POST", body: { token: linkFor(EMAIL, "verify").split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "COMPANY", name: `Retention SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const WORKSPACE = sql(`SELECT id FROM app_lm_workspace WHERE name = 'Retention SPA ${STAMP}'`);
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Retention Unit ${STAMP}` } })).body.id;
const project = async (positionTitle) => (await api("/projects", { method: "POST", token, body: { clientId, positionTitle } })).body.id;
const ARCHIVED = await project("Chief Financial Officer");
const LIVE = await project("Chief Operating Officer");

const file = async (projectId, fullName, slug, employerName) => {
  const added = await api(`/projects/${projectId}/candidates`, {
    method: "POST", token,
    body: { fullName, title: "Finance Director", employerName, linkedinUrl: `https://www.linkedin.com/in/${slug}-${STAMP}/` },
  });
  if (added.status !== 201) throw new Error(`seeding ${fullName} answered ${added.status}: ${JSON.stringify(added.body)}`);
  return added.body.personId;
};
const DORMANT = await file(ARCHIVED, "Tariq Mahmoud", "tariq-mahmoud", "Emaar Industries");
const WORKING = await file(LIVE, "Wael Working", "wael-working", "Live Holdings");
const RESTRICTED = await file(ARCHIVED, "Rami Restricted", "rami-restricted", "Pending Group");
const ADMIN_ID = sql(`SELECT id FROM app_lm_user WHERE email = '${EMAIL}'`);
sql(`UPDATE app_lm_project SET archived_at = now(), archived_by = '${ADMIN_ID}' WHERE id = '${ARCHIVED}'`);
sql(`UPDATE app_lm_person SET created_at = now() - interval '3 years' WHERE workspace_id = '${WORKSPACE}'`);
sql(`UPDATE app_lm_person_activity SET occurred_at = now() - interval '3 years' WHERE workspace_id = '${WORKSPACE}'`);
sql(`UPDATE app_lm_person SET processing_restricted = true, processing_restricted_reason = 'Request pending',
       processing_restricted_at = now() WHERE id = '${RESTRICTED}'`);

// --- the browser ----------------------------------------------------------------------

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const shot = (name) => page.screenshot({ path: join(SHOTS, `data-retention-${name}.png`), fullPage: true });
const dueTable = () => page.getByRole("table", { name: "Due for deletion" });
const toast = (text) => page.getByText(text, { exact: false }).first();

try {
  console.log("\n\x1b[1;36m== DR1  Settings → Data & retention\x1b[0m");
  await step("DR1.1", "an admin finds Data & retention in Settings and opens it", async () => {
    await page.goto(`${WEB}/login`);
    await page.getByPlaceholder("you@firm.com").fill(EMAIL);
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/settings/general`);
    await page.getByRole("link", { name: "Data & retention" }).first().click();
    await page.waitForURL(/\/settings\/data$/);
    await dueTable().waitFor({ timeout: 15000 });
    check("DR1.1", "the period opens on the deployment's 24 months",
      "24", await page.getByRole("combobox", { name: "Retention period" }).inputValue());
    await shot("due");
  });

  await step("DR1.2", "only the untouched person on no live position, unrestricted, is due", async () => {
    const names = await dueTable().getByRole("row").allInnerTexts();
    const listed = names.slice(1).map((row) => row.split("\n")[0]).join(",");
    check("DR1.2", "Tariq alone; Wael is on a live position and Rami restricted", "Tariq Mahmoud", listed);
    const row = names[1] ?? "";
    check("DR1.2b", "the row says employer, one archived position and no owner",
      true, row.includes("Emaar Industries") && row.includes("1 · none live") && row.includes("Nobody"));
  });

  await step("DR1.3", "Keep takes them off the list for another period", async () => {
    await page.getByRole("checkbox", { name: "Select Tariq Mahmoud" }).check();
    await page.getByRole("button", { name: "Keep", exact: true }).click();
    await toast("1 person kept for another 24 months").waitFor({ timeout: 10000 });
    await page.getByText("Nobody is due for deletion").waitFor({ timeout: 10000 });
    check("DR1.3", "the keep is stamped on the person",
      "1", sql(`SELECT count(*) FROM app_lm_person WHERE id = '${DORMANT}' AND retention_kept_at > now() - interval '1 minute'`));
  });

  await step("DR1.4", "a keep two years old is due again, and Delete asks before erasing", async () => {
    sql(`UPDATE app_lm_person SET retention_kept_at = now() - interval '25 months' WHERE id = '${DORMANT}'`);
    await page.reload();
    await dueTable().waitFor({ timeout: 15000 });
    check("DR1.4", "the row says an admin kept them last", true,
      (await dueTable().innerText()).includes("Kept by an admin"));
    await page.getByRole("checkbox", { name: "Select everyone due" }).check();
    await page.getByRole("button", { name: "Delete selected" }).click();
    const dialog = page.getByRole("dialog", { name: "Delete 1 candidate permanently?" });
    await dialog.waitFor({ timeout: 5000 });
    await shot("delete-dialog");
    await dialog.getByRole("button", { name: "Delete permanently" }).click();
    await toast("1 candidate deleted permanently").waitFor({ timeout: 10000 });
    check("DR1.4b", "the person is gone", "0", sql(`SELECT count(*) FROM app_lm_person WHERE id = '${DORMANT}'`));
    check("DR1.4c", "the others stay", "2", sql(`SELECT count(*) FROM app_lm_person WHERE workspace_id = '${WORKSPACE}'`));
    check("DR1.4d", "the erasure is audited as retention's, unsuppressed, with no name", "retention|false|f", sql(
      `SELECT metadata ->> 'reason', metadata ->> 'suppressed', (metadata::text ILIKE '%tariq%')
         FROM app_lm_audit_event WHERE event_type = 'PERSON_ERASED' AND target_id = '${DORMANT}'`));
    check("DR1.4e", "nobody was put on the suppression list", "0",
      sql(`SELECT count(*) FROM app_lm_workspace_suppression WHERE workspace_id = '${WORKSPACE}'`));
  });

  await step("DR1.5", "Never delete turns retention off", async () => {
    await page.getByRole("combobox", { name: "Retention period" }).selectOption("never");
    await page.getByText("Retention is off: candidates are kept until someone deletes them").waitFor();
    await page.getByRole("button", { name: "Save changes" }).click();
    await toast("Retention turned off").waitFor({ timeout: 10000 });
    await page.getByText("Retention is off, so nobody becomes due. Choose a period above to start reviewing.").waitFor();
    check("DR1.5", "the choice is stored as never", "|", sql(
      `SELECT coalesce(candidate_retention_months::text, '') || '|' FROM app_lm_workspace_retention WHERE workspace_id = '${WORKSPACE}'`));
  });

  console.log("\n\x1b[1;36m== DR2  the suppression list\x1b[0m");
  await step("DR2.1", "an admin adds a profile, refused first for a company page", async () => {
    await page.getByRole("button", { name: "Add to suppression list" }).click();
    const dialog = page.getByRole("dialog", { name: "Add to suppression list" });
    await dialog.getByPlaceholder("linkedin.com/in/…").fill("linkedin.com/company/acme");
    await dialog.getByRole("button", { name: "Add to list" }).click();
    await dialog.getByText("Paste a LinkedIn profile link, such as linkedin.com/in/name").waitFor();
    await dialog.getByPlaceholder("linkedin.com/in/…").fill(`linkedin.com/in/board-member-${STAMP}`);
    await dialog.getByRole("radio", { name: /Objected/ }).click();
    await dialog.locator("textarea").fill("Client's board member");
    await shot("suppression-add");
    await dialog.getByRole("button", { name: "Add to list" }).click();
    await toast("Added to the suppression list").waitFor({ timeout: 10000 });
    const list = page.getByRole("table", { name: "Suppression list" });
    await list.waitFor();
    const text = await list.innerText();
    check("DR2.1", "the entry reads kind, reason and note, never the profile", true,
      text.toLowerCase().includes("linkedin") && text.includes("Objected") && text.includes("Client's board member") && !text.includes("board-member"));
    const filed = await api(`/projects/${LIVE}/candidates`, {
      method: "POST", token, body: { fullName: "Board Member", linkedinUrl: `https://www.linkedin.com/in/board-member-${STAMP}/` },
    });
    check("DR2.1b", "the profile can no longer be filed", "PERSON_SUPPRESSED", filed.body?.code);
    await shot("suppression");
  });

  await step("DR2.2", "Remove asks, then lifts the entry", async () => {
    await page.getByRole("table", { name: "Suppression list" }).getByRole("button", { name: "Remove" }).click();
    const dialog = page.getByRole("dialog", { name: "Remove from the suppression list?" });
    await dialog.getByRole("button", { name: "Remove" }).click();
    await page.getByText("Nobody is on the list.", { exact: false }).waitFor({ timeout: 10000 });
    check("DR2.2", "the entry is gone", "0",
      sql(`SELECT count(*) FROM app_lm_workspace_suppression WHERE workspace_id = '${WORKSPACE}'`));
  });

  await step("DR3.1", "the page fits a phone", async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload();
    await page.getByText("Candidate retention").first().waitFor();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    check("DR3.1", "nothing pushes the page sideways", true, overflow <= 0);
    await shot("phone");
  });
} finally {
  await browser.close();
}

console.log(`\n  ${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
