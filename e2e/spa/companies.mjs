// Companies → In universe / Shortlisted / Declined, in a real browser.
//
// 17-companies-executives.sh proves the HTTP contract. This proves what only the screen can: that the
// three stage pages draw exactly their stage's rows, that a row is a person at a company (two people,
// two lines; nobody, one line with its "Add executive" slot), that the drawers' forms reach the
// database, that the Contact section's pencil saves a verified address the grid then lists, that a
// custom column added from the toolbar becomes a header, that the header filters narrow the grid the
// way the API narrows it, and that a pure client seat reads the grid with nothing to write with.
//
// No AI and no paid vendor: the AI buttons and ContactOut's Find buttons are never pressed.
//
// Run from e2e/ (`node spa/companies.mjs`). Every expected value is read from the API or the database
// at run time.

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { readFileSync, mkdirSync, appendFileSync } from "node:fs";
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
const q = (value) => String(value).replace(/'/g, "''");

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

// --- the cast ---------------------------------------------------------------

const LEAD_EMAIL = `lm-e2e-companies-${STAMP}@${DOMAIN}`;
const REP_EMAIL = `lm-e2e-companies-rep-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Carla Companies", email: LEAD_EMAIL, password: PASSWORD, termsAccepted: true } });
await api("/auth/verify", { method: "POST", body: { token: await tokenFor(LEAD_EMAIL, "verify") } });
let token = await login(LEAD_EMAIL);
await api("/onboarding/workspace", { method: "POST", token, body: { name: `Companies SPA ${STAMP}`, companySize: "11-50 people", primaryRegion: "GCC", teamFocus: "Executive search" } });
// A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
token = await login(LEAD_EMAIL);
const clientId = (await api("/clients", { method: "POST", token, body: { customName: "Harbour Group Holding", customDomain: "harbourgroup.example" } })).body.id;
const PROJECT = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Operating Officer" } })).body.id;
const TRIAGE = `/projects/${PROJECT}/triage`;
const PEOPLE = `/projects/${PROJECT}/candidates`;

// Four hand-typed companies at three stages: one with two executives, one with nobody.
const TAG = STAMP.slice(-6);
const ATLAS = `Atlas Holding ${TAG}`;
const BEACON = `Beacon Ports ${TAG}`;
const CEDAR = `Cedar Retail ${TAG}`;
const DHOW = `Dhow Marine ${TAG}`;
const capture = async (companyName, status) =>
  (await api(`${TRIAGE}/capture`, { method: "POST", token, body: {
    companyName, source: "manual", status, industry: "Oil & Energy", companyCountry: "Qatar",
    companyCity: "Doha", numEmployees: 1200, website: "https://example.com" } })).body.id;
const ATLAS_ID = await capture(ATLAS, "inUniverse");
const BEACON_ID = await capture(BEACON, "inUniverse");
const CEDAR_ID = await capture(CEDAR, "shortlisted");
const DHOW_ID = await capture(DHOW, "declined");
const person = (fullName, triageCompanyId, extra = {}) => api(PEOPLE, { method: "POST", token, body: {
  fullName, title: "Chief Financial Officer", seniority: "C-Suite", locationCity: "Doha", locationCountry: "Qatar",
  triageCompanyId, ...extra } });
const LAYLA = `Layla Haddad ${TAG}`;
const OMAR = `Omar Nasser ${TAG}`;
await person(LAYLA, ATLAS_ID, { emails: [{ value: `layla.${TAG}@atlas.example`, kind: "work" }], phones: [{ value: "+971 50 123 4567" }] });
await person(OMAR, ATLAS_ID, { emails: [{ value: `omar.${TAG}@atlas.example` }] });
await person(`Sami Shortlisted ${TAG}`, CEDAR_ID);

// The pure client seat: a representative named on this mandate, signed up through the invitation.
const repInvite = await api(`/projects/${PROJECT}/representatives/invitations`, { method: "POST", token,
  body: { fullName: "Cora Client", position: "Group COO", email: REP_EMAIL } });
if (repInvite.status >= 300) console.log(`representative invitation answered ${repInvite.status}: ${JSON.stringify(repInvite.body)}`);
await api("/onboarding/accept-invitation-signup", { method: "POST",
  body: { token: await tokenFor(REP_EMAIL, "accept-invite"), fullName: "Cora Client", password: PASSWORD } });

const triagePage = async (status, extra = "") => (await api(`${TRIAGE}?status=${status}&size=100${extra}`, { token })).body;
const peopleOf = async () => (await api(PEOPLE, { token })).body.candidates;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1680, height: 1050 } });
let page = await context.newPage();

const shot = (name) => page.screenshot({ path: join(SHOTS, `companies-${name}.png`) });
const stageUrl = (slug) => `${WEB}/projects/${PROJECT}/companies/${slug}`;
const allRows = () => page.locator('[role="table"] [role="row"]');
const headers = async () => (await page.locator('[role="table"] [role="columnheader"]').allInnerTexts()).map((header) => header.trim());
const columnIndex = async (name) => {
  const all = await headers();
  const index = all.findIndex((header) => new RegExp(`^${name}$`, "i").test(header.split("\n")[0].trim()));
  if (index < 0) throw new Error(`no ${name} column among ${all.join(" | ")}`);
  return index;
};
/** Every body row as cell texts, the header row skipped. A cell leading with a logo or avatar ends with its text. */
async function gridRows() {
  const out = [];
  const count = await allRows().count();
  for (let i = 1; i < count; i++) {
    out.push((await allRows().nth(i).locator('[role="cell"]').allInnerTexts()).map((cell) => cell.trim().split("\n").pop().trim()));
  }
  return out;
}
async function column(name) {
  const index = await columnIndex(name);
  return (await gridRows()).map((cells) => cells[index]);
}
/** Poll until `probe` answers `want` — the grid refetches after every write, never instantly. */
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
const openStage = async (slug) => {
  await page.goto(stageUrl(slug));
  await page.waitForSelector('[role="table"]', { timeout: 25000 });
  await page.waitForTimeout(1200);
};
const companyNames = async () => [...new Set(await column("Company"))];
const sidebarCount = async (label) => {
  const text = await page.locator("nav a").filter({ hasText: new RegExp(`^${label}`) }).first().innerText();
  const match = text.trim().match(/(\d+)\s*$/);
  return match ? Number(match[1]) : null;
};
const toolbarButton = (name) => page.getByRole("button", { name, exact: true });
const dialog = (name) => page.getByRole("dialog", { name });

async function signIn(email) {
  await page.goto(`${WEB}/login`);
  await page.getByPlaceholder("you@firm.com").fill(email);
  await page.getByPlaceholder("••••••••").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
}

/** Turns a hidden-by-default grid column on through the toolbar's column picker. */
async function showColumn(label) {
  if ((await headers()).some((header) => header.split("\n")[0].trim().toLowerCase() === label.toLowerCase())) return;
  await page.getByRole("button", { name: /^Columns\s*\d+ hidden/ }).click();
  await page.getByRole("checkbox", { name: label, exact: true }).click();
  await page.keyboard.press("Escape");
  await page.waitForTimeout(400);
}

/** Types into a grid header's own filter, from its column menu. */
async function headerFilter(columnName, ariaLabel, value) {
  const menu = page.getByRole("button", { name: `${columnName} column menu` });
  await page.locator('[role="columnheader"]').filter({ hasText: new RegExp(`^${columnName}`) }).first().hover();
  await menu.click();
  const input = page.getByRole("textbox", { name: ariaLabel });
  await input.fill(value);
  await input.press("Enter");
  await page.waitForTimeout(900);
}

try {
  // ---------------------------------------------------------------- C1.1 access
  section("C1  the three stage pages draw exactly their stage");
  await step("C1.1", "the lead signs in through /login and lands on the universe page", async () => {
    await page.goto(stageUrl("universe"));
    await page.waitForURL(/\/login/, { timeout: 15000 });
    await page.getByPlaceholder("you@firm.com").fill(LEAD_EMAIL);
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await openStage("universe");
    check("C1.1", "the lead signs in through /login and lands on the universe page", true, page.url().endsWith("/companies/universe"));
  });

  await step("C1.2", "In universe lists exactly the seeded in-universe companies", async () => {
    const want = sortedJoin((await triagePage("inUniverse")).companies.map((company) => company.companyName));
    check("C1.2", "In universe lists exactly the seeded in-universe companies", want, await until(async () => sortedJoin(await companyNames()), want));
    await shot("universe");
  });

  await step("C1.3", "a company with two executives renders as two lines, one per person", async () => {
    const rows = await gridRows();
    const nameAt = await columnIndex("Company");
    const execAt = await columnIndex("Executive");
    const atlasPeople = rows.filter((cells) => cells[nameAt] === ATLAS).map((cells) => cells[execAt]);
    check("C1.3", "a company with two executives renders as two lines, one per person", sortedJoin([LAYLA, OMAR]), sortedJoin(atlasPeople));
  });

  await step("C1.4", "a company with nobody keeps one line and its Add executive slot", async () => {
    const lines = (await column("Company")).filter((name) => name === BEACON).length;
    const slot = await page.getByRole("button", { name: `Add an executive at ${BEACON}` }).count();
    check("C1.4", "a company with nobody keeps one line and its Add executive slot", "1/1", `${lines}/${slot}`);
  });

  await step("C1.5", "Shortlisted and Declined each list exactly their own company", async () => {
    await openStage("shortlisted");
    const shortlisted = await until(async () => sortedJoin(await companyNames()), CEDAR);
    await openStage("declined");
    const declined = await until(async () => sortedJoin(await companyNames()), DHOW);
    check("C1.5", "Shortlisted and Declined each list exactly their own company", `${CEDAR} / ${DHOW}`, `${shortlisted} / ${declined}`);
  });

  await step("C1.6", "the sidebar's three counts are the API's", async () => {
    const counts = (await triagePage("inUniverse")).counts;
    const want = `${counts.inUniverse}/${counts.shortlisted}/${counts.declined}`;
    const seen = await until(async () => `${await sidebarCount("In universe")}/${await sidebarCount("Shortlisted")}/${await sidebarCount("Declined")}`, want);
    check("C1.6", "the sidebar's three counts are the API's", want, seen);
    check("C1.6b", "…which are the seeded 2/1/1", "2/1/1", want);
  });

  // ---------------------------------------------------------------- C2 add by hand
  section("C2  a company typed in by hand");
  const FALCON = `Falcon Freight ${TAG}`;
  await step("C2.1", "Add company → 'Not here' → the by-hand form files a new company", async () => {
    await openStage("universe");
    await toolbarButton("Add company").click();
    const drawer = dialog("Add a company");
    await drawer.getByPlaceholder("Search the market…").fill(FALCON);
    await drawer.getByRole("button", { name: /^Not here — add/ }).click();
    await drawer.getByLabel("Company name").fill(FALCON);
    await drawer.getByLabel("City").fill("Muscat");
    await drawer.getByLabel("Employees").fill("450");
    await shot("add-company-form");
    await drawer.getByRole("button", { name: "Add company", exact: true }).click();
    const seen = await until(async () => (await companyNames()).includes(FALCON), true);
    check("C2.1", "Add company → 'Not here' → the by-hand form files a new company, and the grid shows it", true, seen);
  });
  await step("C2.2", "the new company is a MANUAL row In universe with no universe id", async () => {
    check("C2.2", "the new company is a MANUAL row In universe with no universe id", "MANUAL|IN_UNIVERSE|true|450",
      sql(`SELECT source || '|' || status || '|' || (apollo_account_id IS NULL)::text || '|' || coalesce(num_employees::text, '')
           FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}' AND company_name = '${q(FALCON)}'`));
  });

  // ---------------------------------------------------------------- C3 company drawer
  section("C3  the company drawer edits a hand-typed company");
  await step("C3.1", "opening a row, editing its city and saving persists", async () => {
    await page.getByRole("button", { name: `Open ${ATLAS}` }).first().click();
    const drawer = dialog(ATLAS);
    await drawer.getByRole("button", { name: `Edit ${ATLAS}` }).click();
    await drawer.getByLabel("City").fill("Al Khor");
    await drawer.getByRole("button", { name: "Save changes" }).click();
    await page.waitForTimeout(1200);
    const atlas = (await triagePage("inUniverse")).companies.find((company) => company.id === ATLAS_ID);
    check("C3.1", "opening a row, editing its city and saving persists", "Al Khor", atlas?.companyCity);
    await shot("company-drawer-saved");
    await drawer.getByRole("button", { name: "Close" }).first().click().catch(() => page.keyboard.press("Escape"));
  });

  // ---------------------------------------------------------------- C4 add executive
  section("C4  Add executive from the grid's slot");
  const NADIA = `Nadia Karim ${TAG}`;
  let nadiaId = null;
  await step("C4.1", "the slot opens the executive drawer and saving adds a grid line", async () => {
    await openStage("universe");
    await page.getByRole("button", { name: `Add an executive at ${BEACON}` }).click();
    const drawer = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Add executive" }) });
    await drawer.getByLabel("Full name").fill(NADIA);
    await drawer.getByLabel("Title").fill("Chief Commercial Officer");
    await drawer.getByLabel("Nationality").selectOption("Emirati");
    await drawer.getByLabel("Gender").selectOption("female");
    await shot("add-executive-form");
    await drawer.getByRole("button", { name: "Add executive", exact: true }).click();
    await page.waitForTimeout(1500);
    const nameAt = await columnIndex("Company");
    const execAt = await columnIndex("Executive");
    const seen = await until(async () => (await gridRows()).some((cells) => cells[nameAt] === BEACON && cells[execAt] === NADIA), true);
    check("C4.1", "the slot opens the executive drawer and saving adds a grid line at that company", true, seen);
  });
  await step("C4.2", "the executive is a row at that company with the gender and nationality group chosen", async () => {
    nadiaId = sql(`SELECT id FROM app_lm_project_candidate WHERE project_id = '${PROJECT}' AND full_name = '${q(NADIA)}'`);
    check("C4.2", "the executive is a row at that company with the gender and nationality group chosen",
      `${BEACON_ID}|FEMALE|Emirati|MANUAL|Chief Commercial Officer`,
      sql(`SELECT triage_company_id || '|' || gender || '|' || nationality || '|' || source || '|' || title
           FROM app_lm_project_candidate WHERE id = '${nadiaId}'`));
  });
  await step("C4.3", "the Beacon line no longer offers the empty Add executive slot", async () => {
    check("C4.3", "the Beacon line no longer offers the empty Add executive slot", 0,
      await until(() => page.getByRole("button", { name: `Add an executive at ${BEACON}` }).count(), 0));
  });

  // ---------------------------------------------------------------- C5 contact section
  section("C5  the Contact section's pencil");
  const NADIA_EMAIL = `nadia.${TAG}@beacon.example`;
  await step("C5.1", "the pencil makes the lines editable; an added, verified email saves", async () => {
    // The drawer stays open on the profile it just made.
    const drawer = page.getByRole("dialog").last();
    await drawer.getByRole("button", { name: "Edit contact" }).click();
    await drawer.getByRole("button", { name: "Add email" }).click();
    await drawer.getByRole("textbox", { name: "Email 1", exact: true }).fill(NADIA_EMAIL);
    await drawer.getByRole("switch", { name: "Email 1 verified" }).click();
    await shot("contact-editing");
    await drawer.getByRole("button", { name: "Save", exact: true }).click();
    await page.waitForTimeout(1500);
    check("C5.1", "the pencil makes the lines editable; an added, verified email saves to the ledger", "EMAIL|true|MANUAL|Verified by researcher",
      sql(`SELECT channel || '|' || verified || '|' || source || '|' || coalesce(status, '') FROM app_lm_candidate_contact
           WHERE candidate_id = '${nadiaId}' AND value = '${q(NADIA_EMAIL)}'`));
    await drawer.getByRole("button", { name: "Close" }).first().click().catch(() => page.keyboard.press("Escape"));
  });
  await step("C5.2", "the grid's Email column lists the saved address on her line", async () => {
    await showColumn("Email");
    const execAt = await columnIndex("Executive");
    const emailAt = await columnIndex("Email");
    const seen = await until(async () => (await gridRows()).find((cells) => cells[execAt] === NADIA)?.[emailAt], NADIA_EMAIL);
    check("C5.2", "the grid's Email column lists the saved address on her line", NADIA_EMAIL, seen);
    await shot("email-column");
  });

  // ---------------------------------------------------------------- C6 moves
  section("C6  moving a company between stages");
  await step("C6.1", "the row's Shortlist action moves it off In universe and onto Shortlisted", async () => {
    await openStage("universe");
    await page.getByRole("button", { name: `Shortlist: ${BEACON}` }).first().click();
    const gone = await until(async () => (await companyNames()).includes(BEACON), false);
    await openStage("shortlisted");
    const arrived = await until(async () => (await companyNames()).includes(BEACON), true);
    check("C6.1", "the row's Shortlist action moves it off In universe and onto Shortlisted", "false/true", `${gone}/${arrived}`);
    check("C6.1b", "…and the database agrees", "SHORTLISTED",
      sql(`SELECT status FROM app_lm_project_triage_company WHERE id = '${BEACON_ID}'`));
  });
  await step("C6.2", "the drawer's Decline moves a shortlisted company onto Declined", async () => {
    await page.getByRole("button", { name: `Open ${CEDAR}` }).first().click();
    await dialog(CEDAR).getByRole("button", { name: "Decline", exact: true }).click();
    const gone = await until(async () => (await companyNames()).includes(CEDAR), false);
    await openStage("declined");
    const arrived = await until(async () => (await companyNames()).includes(CEDAR), true);
    check("C6.2", "the drawer's Decline moves a shortlisted company onto Declined", "false/true", `${gone}/${arrived}`);
  });
  await step("C6.3", "the sidebar counts follow the moves", async () => {
    const counts = (await triagePage("inUniverse")).counts;
    const want = `${counts.inUniverse}/${counts.shortlisted}/${counts.declined}`;
    check("C6.3", "the sidebar counts follow the moves (2/1/2 after adding Falcon, moving Beacon and Cedar)", "2/1/2",
      await until(async () => `${await sidebarCount("In universe")}/${await sidebarCount("Shortlisted")}/${await sidebarCount("Declined")}`, want));
  });

  // ---------------------------------------------------------------- C7 custom columns
  section("C7  a column of the mandate's own");
  const COLUMN = "Board Seat";
  await step("C7.1", "Columns → Add defines a company column and the grid grows its header", async () => {
    await openStage("universe");
    await page.getByRole("button", { name: "Columns", exact: true }).last().click();
    const modal = dialog("Columns on this mandate");
    await modal.getByLabel("New column name").fill(COLUMN);
    await modal.getByLabel("What the new column is about").selectOption("company");
    await modal.getByRole("button", { name: "Add", exact: true }).click();
    await page.waitForTimeout(800);
    await shot("manage-columns");
    await modal.getByRole("button", { name: "Done" }).click();
    const seen = await until(async () => (await headers()).some((header) => header.split("\n")[0].trim().toLowerCase() === COLUMN.toLowerCase()), true);
    check("C7.1", "Columns → Add defines a company column and the grid grows its header", true, seen);
    check("C7.1b", "…stored as a company column of this project", "company|boardSeat",
      sql(`SELECT lower(target) || '|' || field_key FROM app_lm_project_custom_column WHERE project_id = '${PROJECT}' AND label = '${COLUMN}'`));
  });
  await step("C7.2", "a value set in the company drawer's Your columns persists and fills the cell", async () => {
    await page.getByRole("button", { name: `Open ${ATLAS}` }).first().click();
    const drawer = dialog(ATLAS);
    await drawer.getByLabel(COLUMN).fill("Chair");
    await drawer.getByRole("button", { name: "Save", exact: true }).first().click();
    await page.waitForTimeout(1200);
    const atlas = (await triagePage("inUniverse")).companies.find((company) => company.id === ATLAS_ID);
    check("C7.2", "a value set in the company drawer's Your columns persists", "Chair", atlas?.customFields?.boardSeat);
    await drawer.getByRole("button", { name: "Close" }).first().click().catch(() => page.keyboard.press("Escape"));
    const nameAt = await columnIndex("Company");
    const seen = await until(async () => {
      const at = await columnIndex(COLUMN);
      return (await gridRows()).filter((cells) => cells[nameAt] === ATLAS).map((cells) => cells[at]).join(",");
    }, "Chair,Chair");
    check("C7.2b", "…and the cell shows it on both of the company's lines", "Chair,Chair", seen);
  });

  // ---------------------------------------------------------------- C8 header filters
  section("C8  the header filters narrow the grid as the API does");
  await step("C8.1", "the Company header filter narrows to the matching company, and the count agrees", async () => {
    await openStage("universe");
    await headerFilter("Company", "Filter by company name", "Atlas");
    const api = await triagePage("inUniverse", "&q=Atlas");
    const want = sortedJoin(api.companies.map((company) => company.companyName));
    check("C8.1", "the Company header filter narrows to the matching company", want, await until(async () => sortedJoin(await companyNames()), want));
    const bar = await page.locator("text=/\\d+\\s*-\\s*\\d+ of \\d+|0 results/").first().innerText().catch(() => "");
    check("C8.1b", "…and the pager's total is the API's totalCount", api.totalCount, bar.includes(" of ") ? Number(bar.split(" of ")[1].replace(/\D/g, "")) : bar);
    await shot("filter-company");
  });
  await step("C8.2", "the Executive header filter keeps only the matching person, not their colleague", async () => {
    await openStage("universe");
    await headerFilter("Executive", "Filter by executive name", "Layla");
    const api = await triagePage("inUniverse", "&executiveQuery=Layla");
    const seen = await until(async () => sortedJoin(await column("Executive")), LAYLA);
    check("C8.2", "the Executive header filter keeps only the matching person, not their colleague", LAYLA, seen);
    check("C8.2b", "…over the one company the API answers for it", `1|${ATLAS}`, `${api.totalCount}|${api.companies.map((c) => c.companyName).join(",")}`);
  });

  // ---------------------------------------------------------------- C9 client seat
  section("C9  a pure client seat reads the grid, and writes nothing");
  await step("C9.1", "the representative sees the same In universe rows", async () => {
    await context.clearCookies();
    await page.close();
    page = await context.newPage();
    await signIn(REP_EMAIL);
    await openStage("universe");
    const want = sortedJoin((await triagePage("inUniverse")).companies.map((company) => company.companyName));
    check("C9.1", "the representative sees the same In universe rows", want, await until(async () => sortedJoin(await companyNames()), want));
    await shot("client-universe");
  });
  await step("C9.2", "no add, import, columns or row-action controls are offered to a client seat", async () => {
    const offered = [];
    for (const name of ["Add company", "Add executive", "Import", "Columns"]) {
      if (await page.getByRole("button", { name, exact: true }).count()) offered.push(name);
    }
    if (await page.getByRole("button", { name: /^Add an executive at/ }).count()) offered.push("Add-executive slot");
    if (await page.getByRole("button", { name: /^(Shortlist|Decline|Back to universe): / }).count()) offered.push("move actions");
    if (await page.getByRole("button", { name: /^Remove .* from this mandate$/ }).count()) offered.push("remove");
    if (await page.getByRole("combobox", { name: /^Status for / }).count()) offered.push("status select");
    check("C9.2", "no add, import, columns or row-action controls are offered to a client seat", "", offered.join(", "));
    check("C9.2b", "…while Export is", 1, await page.getByRole("button", { name: "Export", exact: true }).count());
  });
  await step("C9.3", "the company drawer opens read-only for a client seat", async () => {
    await page.getByRole("button", { name: `Open ${ATLAS}` }).first().click();
    const drawer = dialog(ATLAS);
    await drawer.waitFor();
    const editable = [];
    if (await drawer.getByRole("button", { name: `Edit ${ATLAS}` }).count()) editable.push("edit pencil");
    if (await drawer.getByRole("button", { name: /^(Shortlist|Decline|Remove)$/ }).count()) editable.push("footer moves");
    const readOnly = await drawer.getByLabel("Note on this company").getAttribute("readonly");
    check("C9.3", "the company drawer opens read-only for a client seat", "|readonly", `${editable.join(",")}|${readOnly === null ? "editable" : "readonly"}`);
    await shot("client-drawer");
    await drawer.getByRole("button", { name: "Close" }).first().click().catch(() => page.keyboard.press("Escape"));
  });
  await step("C9.4", "the executive drawer opens with no section pencils for a client seat", async () => {
    await page.getByRole("button", { name: new RegExp(LAYLA) }).first().click();
    await page.waitForTimeout(800);
    const drawer = page.getByRole("dialog").last();
    check("C9.4", "the executive drawer opens with no section pencils for a client seat", 0,
      await drawer.getByRole("button", { name: /^Edit / }).count());
    await shot("client-executive");
  });
} finally {
  await browser.close();
  console.log(`\n\x1b[1;36m---- companies.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);
  process.exitCode = failed === 0 ? 0 : 1;
}
