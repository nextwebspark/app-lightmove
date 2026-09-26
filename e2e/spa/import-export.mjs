// The spreadsheet in both directions, in a real browser: Import from the Companies toolbar, and
// Export of a stage back out as a file.
//
// 18-import-export.sh proves the HTTP contract. This proves the screen's half: that a file picked in
// the dialog is read, that its mapping step shows every column landing on a known field with no
// assistant involved (a file built from the downloadable template maps by name alone — the e2e stack
// runs with the model off, and this file must never need it), that committing fills the grid, and
// that Export hands the browser a file carrying exactly what the grid was showing — the whole stage,
// or the stage as its header filters narrowed it — for the lead and for a client seat alike.
//
// Run from e2e/ (`node spa/import-export.mjs`). Expected values come from the API or the database.

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, mkdirSync, appendFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = join(RUN_DIR, "api.log");
const WEB = process.env.WEB ?? "http://localhost:5173";
const API = process.env.API ?? "http://localhost:8080/api/v1";
const PG_URL = process.env.PG_URL ?? "postgresql://lm_app:lm@localhost:55432/lightmove";
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
const note = (id, what) => { console.log(`  \x1b[2mNOTE\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "NOTE", what); };
const section = (title) => console.log(`\n\x1b[1;36m== ${title}\x1b[0m`);
const check = (id, what, expected, actual) =>
  String(expected) === String(actual) ? pass(id, what) : fail(id, what, `expected [${expected}] got [${actual}]`);
// One case failing must not hide the twenty behind it, so each one runs inside its own guard.
const step = async (id, what, body) => {
  try { await body(); } catch (error) { fail(id, what, `threw: ${error.message.split("\n")[0].slice(0, 200)}`); }
};

const sql = (query) => execFileSync("psql", [PG_URL, "-Atc", query]).toString().trim();
const num = (query) => Number(sql(query));
/** The audit event is written after the response, so a read straight away can race it. */
async function awaitSql(query, want, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  let last = sql(query);
  while (last !== String(want) && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 250));
    last = sql(query);
  }
  return last;
}

// The verification link belongs to a specific recipient: LogEmailSender prints `To:` before the body,
// so the last link that follows this address's header line is the one we want.
function linkFor(address, kind = "verify") {
  let mine = false, found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(new RegExp(`http://localhost:5173/auth/${kind}\\?token=[A-Za-z0-9_%.~-]+`));
      if (match) found = match[0];
    }
  }
  return found;
}
/** The mail is logged a moment after the request that sends it answers. */
async function tokenFor(address, kind) {
  for (let i = 0; i < 50; i++) {
    const link = linkFor(address, kind);
    if (link) return link.split("token=")[1];
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`no ${kind} link for ${address} in ${API_LOG}`);
}

const api = async (path, { method = "GET", body, token } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let parsed = null;
  try { parsed = text ? JSON.parse(text) : null; } catch { parsed = text; }
  return { status: response.status, body: parsed };
};
const login = async (email) =>
  (await api("/auth/login", { method: "POST", body: { email, password: PASSWORD } })).body.accessToken;

/** A CSV as the server writes it: a UTF-8 BOM for Excel, CRLF line ends. No value here holds a comma. */
const csvLines = (text) => text.replace(/^﻿/, "").split(/\r?\n/).filter((line) => line.length > 0);

// --- the cast ---------------------------------------------------------------

const LEAD_EMAIL = `lm-e2e-impexp-${STAMP}@${DOMAIN}`;
const REP_EMAIL = `lm-e2e-impexp-rep-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Ivy Importer", email: LEAD_EMAIL, password: PASSWORD, termsAccepted: true } });
await api("/auth/verify", { method: "POST", body: { token: await tokenFor(LEAD_EMAIL, "verify") } });
let token = await login(LEAD_EMAIL);
await api("/onboarding/workspace", { method: "POST", token, body: { name: `Import SPA ${STAMP}`, companySize: "11-50 people", primaryRegion: "GCC", teamFocus: "Executive search" } });
// A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
token = await login(LEAD_EMAIL);
const clientId = (await api("/clients", { method: "POST", token, body: { customName: "Import Holding", customDomain: "importholding.example" } })).body.id;
const PROJECT = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;

// A mandate column of its own, defined before the template is read so the template carries it and
// the file stays a file of known headers.
const CUSTOM = "Board Seat";
await api(`/projects/${PROJECT}/custom-columns`, { method: "POST", token, body: { target: "company", label: CUSTOM } });

await api(`/projects/${PROJECT}/representatives/invitations`, { method: "POST", token,
  body: { fullName: "Cora Client", position: "Group CFO", email: REP_EMAIL } });
await api("/onboarding/accept-invitation-signup", { method: "POST",
  body: { token: await tokenFor(REP_EMAIL, "accept-invite"), fullName: "Cora Client", password: PASSWORD } });

// --- the file ---------------------------------------------------------------

const TAG = STAMP.slice(-6);
const template = await api(`/projects/${PROJECT}/import/template`, { token });
const TEMPLATE_HEADERS = csvLines(template.body)[0].split(",");
const FALCON = `Falcon Logistics ${TAG}`;
const DUNE = `Dune Energy ${TAG}`;
const OASIS = `Oasis Foods ${TAG}`;
const ROWS = [
  { Company: FALCON, Sector: "Logistics", Country: "United Arab Emirates", City: "Dubai", Employees: "1200", Website: "https://falcon.example",
    Name: `Aisha Rahman ${TAG}`, Title: "Chief Financial Officer", Level: "C-Suite", Email: `aisha.${TAG}@falcon.example`, Phone: "+971 50 111 2233",
    LinkedIn: `https://www.linkedin.com/in/aisha-${TAG}`, [CUSTOM]: "Chair" },
  { Company: FALCON, Sector: "Logistics", Country: "United Arab Emirates", City: "Dubai", Employees: "1200", Website: "https://falcon.example",
    Name: `Omar Siddiqui ${TAG}`, Title: "Chief Operating Officer", Level: "C-Suite", Email: `omar.${TAG}@falcon.example`, Phone: "",
    LinkedIn: `https://www.linkedin.com/in/omar-${TAG}`, [CUSTOM]: "Chair" },
  { Company: DUNE, Sector: "Oil & Energy", Country: "Saudi Arabia", City: "Riyadh", Employees: "3000", Website: "https://dune.example",
    Name: `Layla Haddad ${TAG}`, Title: "Group CFO", Level: "N-1", Email: `layla.${TAG}@dune.example`, Phone: "",
    LinkedIn: `https://www.linkedin.com/in/layla-${TAG}`, [CUSTOM]: "" },
  { Company: OASIS, Sector: "Food & Beverages", Country: "Qatar", City: "Doha", Employees: "450", Website: "https://oasis.example" },
];
const FILE = join(RUN_DIR, `spa-import-${TAG}.csv`);
writeFileSync(FILE, [TEMPLATE_HEADERS.join(","), ...ROWS.map((row) => TEMPLATE_HEADERS.map((header) => row[header] ?? "").join(","))].join("\n") + "\n");

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
let context = await browser.newContext({ viewport: { width: 1680, height: 1050 }, acceptDownloads: true });
let page = await context.newPage();

const shot = (name) => page.screenshot({ path: join(SHOTS, `import-export-${name}.png`) });
const UNIVERSE_URL = `${WEB}/projects/${PROJECT}/companies/universe`;
const allRows = () => page.locator('[role="table"] [role="row"]');
const headers = async () => (await page.locator('[role="table"] [role="columnheader"]').allInnerTexts()).map((header) => header.trim());
const columnIndex = async (name) => {
  const all = await headers();
  const index = all.findIndex((header) => new RegExp(`^${name}$`, "i").test(header.split("\n")[0].trim()));
  if (index < 0) throw new Error(`no ${name} column among ${all.join(" | ")}`);
  return index;
};
async function column(name) {
  const index = await columnIndex(name);
  const out = [];
  for (let i = 1, n = await allRows().count(); i < n; i++) {
    out.push((await allRows().nth(i).locator('[role="cell"]').allInnerTexts())[index].trim().split("\n").pop().trim());
  }
  return out;
}
async function until(probe, want, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    try { last = await probe(); if (String(last) === String(want)) return last; } catch (error) { last = `(threw ${error.message.slice(0, 80)})`; }
    await page.waitForTimeout(250);
  }
  return last;
}
const sortedJoin = (values) => [...values].sort().join(" | ");
const openUniverse = async () => {
  await page.goto(UNIVERSE_URL);
  await page.waitForSelector('[role="table"]', { timeout: 25000 });
  await page.waitForTimeout(1200);
};
async function signIn(email) {
  await page.goto(`${WEB}/login`);
  await page.getByPlaceholder("you@firm.com").fill(email);
  await page.getByPlaceholder("••••••••").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
}
/** Presses the toolbar's Export and reads the file the browser was handed. */
async function exportFile() {
  const [download] = await Promise.all([
    page.waitForEvent("download", { timeout: 20000 }),
    page.getByRole("button", { name: "Export", exact: true }).click(),
  ]);
  const path = await download.path();
  return { name: download.suggestedFilename(), lines: csvLines(readFileSync(path, "utf8")) };
}
/** The same stage as the server exports it — the API's own answer to what the file must hold. */
const apiExport = async (query, as = token) => {
  const response = await fetch(`${API}/projects/${PROJECT}/export/companies?${query}`, { headers: { Authorization: `Bearer ${as}` } });
  return csvLines(await response.text());
};
/** The grid's lines for a stage from the two reads it is composed of: a line per person at a company,
 *  one for a company with nobody, and — unfiltered, on the universe — the people at no company. */
async function expectedLines() {
  const companies = (await api(`/projects/${PROJECT}/triage?status=inUniverse&size=100`, { token })).body.companies;
  const people = (await api(`/projects/${PROJECT}/candidates`, { token })).body.candidates;
  let lines = 0;
  for (const company of companies) lines += Math.max(1, people.filter((p) => p.triageCompanyId === company.id).length);
  return lines + people.filter((p) => !p.triageCompanyId).length;
}
async function headerFilter(columnName, ariaLabel, value) {
  await page.locator('[role="columnheader"]').filter({ hasText: new RegExp(`^${columnName}`, "i") }).first().hover();
  await page.getByRole("button", { name: `${columnName} column menu` }).click();
  const input = page.getByRole("textbox", { name: ariaLabel });
  await input.fill(value);
  await input.press("Enter");
  await page.waitForTimeout(900);
}

try {
  section("I1  the template the file is built from");
  await step("I1.1", "the template's header row carries the mandate's own column", async () => {
    check("I1.1", "the template's header row carries the mandate's own column", true, TEMPLATE_HEADERS.includes(CUSTOM));
    note("I1.1a", `template headers: ${TEMPLATE_HEADERS.join(",")}`);
  });

  // ---------------------------------------------------------------- import
  section("I1  Import from the Companies toolbar");
  await step("I1.2", "the lead signs in and opens the Import dialog", async () => {
    await signIn(LEAD_EMAIL);
    await openUniverse();
    await page.getByRole("button", { name: "Import", exact: true }).click();
    check("I1.2", "the lead signs in and opens the Import dialog", 1,
      await page.getByRole("dialog", { name: "Import companies and people" }).count());
  });
  const modal = page.getByRole("dialog", { name: "Import companies and people" });
  await step("I1.3", "picking the file reads it into the mapping step", async () => {
    await modal.getByLabel("Spreadsheet to import").setInputFiles(FILE);
    await modal.getByRole("button", { name: /^Import \d+ rows?$/ }).waitFor({ timeout: 20000 });
    const summary = await modal.locator("p").filter({ hasText: /·/ }).first().innerText();
    note("I1.3a", `mapping line: ${summary}`);
    check("I1.3", "picking the file reads it into the mapping step, every row counted", true, summary.includes(`${ROWS.length} rows`));
    await shot("mapping");
  });
  await step("I1.4", "every column is matched by name — no assistant, no header-matcher fallback", async () => {
    const text = await modal.innerText();
    check("I1.4", "every column is matched by name — no assistant, no header-matcher fallback",
      "true/false/false", `${text.includes("every column matched by name")}/${text.includes("matched by the assistant")}/${text.includes("could not be reached")}`);
  });
  await step("I1.5", "each header lands on a known field (or the mandate's own column), none ignored or new", async () => {
    const choices = [];
    for (const header of TEMPLATE_HEADERS) {
      const select = modal.getByRole("combobox", { name: `What "${header}" imports as` });
      const value = await select.inputValue();
      const label = await select.locator("option:checked").innerText();
      choices.push(`${header}=${value.startsWith("field:") || value.startsWith("custom:") ? "known" : value}(${label.trim()})`);
    }
    note("I1.5a", choices.join("; "));
    check("I1.5", "each header lands on a known field (or the mandate's own column), none ignored or new",
      TEMPLATE_HEADERS.length, choices.filter((choice) => choice.includes("=known")).length);
    // The option labels are the fields' own names, which for these two are the headers themselves, so
    // the wire token is what tells the employer's name from the person's.
    check("I1.5b", "Company imports as the company's name, Name as the person's, under their optgroups",
      "field:companyName(Company)/field:candidateName(Person)",
      `${await modal.getByRole("combobox", { name: 'What "Company" imports as' }).inputValue()}(${await modal.getByRole("combobox", { name: 'What "Company" imports as' }).locator("option:checked").evaluate((option) => option.parentElement.label)})/` +
      `${await modal.getByRole("combobox", { name: 'What "Name" imports as' }).inputValue()}(${await modal.getByRole("combobox", { name: 'What "Name" imports as' }).locator("option:checked").evaluate((option) => option.parentElement.label)})`);
    check("I1.5c", `"${CUSTOM}" fills the mandate's existing column`, CUSTOM,
      (await modal.getByRole("combobox", { name: `What "${CUSTOM}" imports as` }).locator("option:checked").innerText()).trim());
  });
  await step("I1.6", "committing reports the tally and writes nothing through any door but CSV", async () => {
    await modal.getByRole("button", { name: /^Import \d+ rows?$/ }).click();
    await modal.getByRole("button", { name: "Done" }).waitFor({ timeout: 20000 });
    const tally = await modal.locator("ul").first().innerText();
    await shot("tally");
    check("I1.6", "the tally reads three companies and three people added", "true/true",
      `${/3\s+companies\s+added/.test(tally)}/${/3\s+people\s+added/.test(tally)}`);
    await modal.getByRole("button", { name: "Done" }).click();
  });
  await step("I1.7", "the imported rows are in the database with source CSV", async () => {
    check("I1.7", "three companies In universe with source CSV and no universe id", "3",
      sql(`SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}' AND source = 'CSV' AND status = 'IN_UNIVERSE' AND apollo_account_id IS NULL`));
    check("I1.7b", "three people with source CSV, two of them at Falcon", "3/2",
      `${sql(`SELECT count(*) FROM app_lm_project_candidate WHERE project_id = '${PROJECT}' AND source = 'CSV'`)}/${sql(
        `SELECT count(*) FROM app_lm_project_candidate c JOIN app_lm_project_triage_company t ON t.id = c.triage_company_id WHERE c.project_id = '${PROJECT}' AND t.company_name = '${FALCON}'`)}`);
    check("I1.7c", "each address is a contact row that came through the CSV door", "3",
      sql(`SELECT count(*) FROM app_lm_candidate_contact cc JOIN app_lm_project_candidate c ON c.id = cc.candidate_id WHERE c.project_id = '${PROJECT}' AND cc.channel = 'EMAIL' AND cc.source = 'CSV'`));
    check("I1.7d", "the custom column's value landed on Falcon", "Chair",
      sql(`SELECT custom_fields->>'boardSeat' FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}' AND company_name = '${FALCON}'`));
  });
  await step("I1.8", "the grid shows the imported companies and people without a reload", async () => {
    const want = sortedJoin([FALCON, FALCON, DUNE, OASIS]);
    check("I1.8", "the grid shows one line per imported person, and one for the company with nobody", want,
      await until(async () => sortedJoin(await column("Company")), want));
    check("I1.8b", "…naming the imported people", sortedJoin([`Aisha Rahman ${TAG}`, `Omar Siddiqui ${TAG}`, `Layla Haddad ${TAG}`]),
      sortedJoin((await column("Executive")).filter((name) => name && name !== "—")));
    await shot("imported-grid");
  });

  // ---------------------------------------------------------------- export
  section("I2  Export from the Companies toolbar");
  // One executive typed at no company, so the whole-stage file carries the unmapped line too.
  await api(`/projects/${PROJECT}/candidates`, { method: "POST", token, body: { fullName: `Unplaced Person ${TAG}`, title: "Board Advisor" } });
  let whole = null;
  await step("I2.1", "Export downloads a CSV of the stage", async () => {
    await openUniverse();
    whole = await exportFile();
    note("I2.1a", `file: ${whole.name}`);
    check("I2.1", "Export downloads a CSV of the stage", true, whole.name.endsWith(".csv") && whole.lines.length > 1);
  });
  await step("I2.2", "the header spells the Links out as Website and Company LinkedIn and carries the custom column", async () => {
    const header = whole.lines[0].split(",");
    check("I2.2", "the header spells the Links out as Website and Company LinkedIn and carries the custom column",
      "true/true/true", `${header.includes("Website")}/${header.includes("Company LinkedIn")}/${header.includes(CUSTOM)}`);
    check("I2.2b", "…and is the header the API writes", (await apiExport("status=inUniverse"))[0], whole.lines[0]);
  });
  await step("I2.3", "one line per grid row: the whole stage, the unmapped person included", async () => {
    const want = await expectedLines();
    check("I2.3", "one line per grid row: the whole stage, the unmapped person included", want, whole.lines.length - 1);
    check("I2.3b", "…the unmapped person is on it", true, whole.lines.some((line) => line.includes(`Unplaced Person ${TAG}`)));
    const header = whole.lines[0].split(",");
    const falcon = whole.lines.find((line) => line.includes(`Aisha Rahman ${TAG}`)).split(",");
    check("I2.3c", "…and Falcon's line carries its website and its custom value", "https://falcon.example|Chair",
      `${falcon[header.indexOf("Website")]}|${falcon[header.indexOf(CUSTOM)]}`);
  });
  await step("I2.4", "with the Company header filter set, the file is narrowed the same way", async () => {
    await headerFilter("Company", "Filter by company name", "Falcon");
    await until(async () => sortedJoin(await column("Company")), sortedJoin([FALCON, FALCON]));
    const narrowed = await exportFile();
    const api = await apiExport("status=inUniverse&q=Falcon");
    check("I2.4", "with the Company header filter set, the file is narrowed the same way", `2/${api.length - 1}`,
      `${narrowed.lines.length - 1}/${api.length - 1}`);
    check("I2.4b", "…holding only Falcon's lines, the unmapped person dropped", true,
      narrowed.lines.slice(1).every((line) => line.startsWith(FALCON)));
  });
  await step("I2.5", "with the Executive header filter set, the file keeps only that person", async () => {
    await openUniverse();
    await headerFilter("Executive", "Filter by executive name", "Aisha");
    await until(async () => sortedJoin(await column("Executive")), `Aisha Rahman ${TAG}`);
    const narrowed = await exportFile();
    check("I2.5", "with the Executive header filter set, the file keeps only that person", `1|true`,
      `${narrowed.lines.length - 1}|${narrowed.lines[1]?.includes(`Aisha Rahman ${TAG}`)}`);
  });
  await step("I2.6", "every export is on the audit trail, the filtered ones marked not whole", async () => {
    // Three from the toolbar, plus the two apiExport reads above (one whole, one filtered): the audit
    // does not care which client asked.
    check("I2.6", "every export is on the audit trail, the filtered ones marked not whole", "5|2",
      await awaitSql(`SELECT count(*) || '|' || count(*) FILTER (WHERE metadata->>'wholeStage' = 'true') FROM app_lm_audit_event
           WHERE event_type = 'COMPANIES_EXPORTED' AND target_id = '${PROJECT}'`, "5|2"));
  });

  // ---------------------------------------------------------------- client seat
  section("I3  a client seat takes the file too");
  await step("I3.1", "the representative's Export downloads the same whole stage", async () => {
    await context.close();
    context = await browser.newContext({ viewport: { width: 1680, height: 1050 }, acceptDownloads: true });
    page = await context.newPage();
    await signIn(REP_EMAIL);
    await openUniverse();
    check("I3.1a", "no Import button is offered to a client seat", 0, await page.getByRole("button", { name: "Import", exact: true }).count());
    const file = await exportFile();
    check("I3.1", "the representative's Export downloads the same whole stage", `${whole.lines.length}|${whole.lines[0]}`,
      `${file.lines.length}|${file.lines[0]}`);
    const repId = sql(`SELECT id FROM app_lm_user WHERE email = '${REP_EMAIL}'`);
    check("I3.1b", "…recorded against the representative", "1",
      await awaitSql(`SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'COMPANIES_EXPORTED' AND target_id = '${PROJECT}' AND actor_user_id = '${repId}'`, "1"));
    await shot("client-export");
  });
} finally {
  await browser.close();
  console.log(`\n\x1b[1;36m---- import-export.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);
  process.exitCode = failed === 0 ? 0 : 1;
}
