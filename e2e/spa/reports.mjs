// The talent mapping report, in a real browser.
//
// 19-reports-map-activity.sh proves the report's arithmetic over HTTP. This proves what only the
// screen can: that the chapter rail walks the four chapters with ?chapter= following, that each
// chapter's headline tile states the figure GET /report computed, that the staff-only Researcher
// performance card is drawn for the lead and not for a client seat, that the two cross-mandate
// benchmarks say they are not built, that the projects list's side panel phrases the seeding as
// lib/activity.ts does, and that the Table | Map toggle follows the Mapbox configuration.
//
// Run from e2e/ (`node spa/reports.mjs`). Seeds its own mandate through the ordinary APIs — typed-in
// companies, no Apollo universe, no AI — and reads every expected value back from the API.

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
const checkHas = (id, what, needle, text) =>
  text.includes(needle) ? pass(id, what) : fail(id, what, `expected [${needle}] in [${text.slice(0, 240)}]`);
const step = async (id, what, body) => {
  try { await body(); } catch (error) { fail(id, what, `threw: ${error.message.split("\n")[0].slice(0, 200)}`); }
};

const sql = (query) => execFileSync("psql", [PG_URL, "-Atc", query]).toString().trim();
const num = (query) => Number(sql(query));

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

const api = async (path, { method = "GET", body, token } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

/** Poll the API until `predicate` holds on the body, or give back the last body read. */
async function waitApi(path, token, predicate, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    last = (await api(path, { token })).body;
    try { if (predicate(last)) return last; } catch { /* shape not there yet */ }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  return last;
}

// --- the cast and the seed -------------------------------------------------------

const LEAD_NAME = "Rhea Reporter";
const EMAIL = `lm-e2e-reports-${STAMP}@${DOMAIN}`;
const REP_EMAIL = `lm-e2e-reports-rep-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: LEAD_NAME, email: EMAIL, password: PASSWORD, termsAccepted: true } });
await api("/auth/verify", { method: "POST", body: { token: linkFor(EMAIL, "verify").split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { name: `Reports SPA ${STAMP}`, companySize: "11-50 people", primaryRegion: "GCC", teamFocus: "Executive search" } });
// A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Report Holding ${STAMP}`, customDomain: `reportholding${STAMP}.example` } })).body.id;
const TITLE = "Chief Financial Officer";
const PROJECT = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: TITLE } })).body.id;

// The client seat: a representative attached to this mandate, signed up through the invite link.
await api(`/projects/${PROJECT}/representatives/invitations`, { method: "POST", token, body: { fullName: "Cora Client", position: "Group CFO", email: REP_EMAIL } });
await api("/onboarding/accept-invitation-signup", { method: "POST", body: { token: decodeURIComponent(linkFor(REP_EMAIL, "accept-invite").split("token=")[1]), fullName: "Cora Client", password: PASSWORD } });
const repToken = (await api("/auth/login", { method: "POST", body: { email: REP_EMAIL, password: PASSWORD } })).body.accessToken;

// Read through the seam the report uses (it drafts nothing), so the seeded packages are in its currency.
const CCY = (await api(`/projects/${PROJECT}/position/compensation`, { token })).body?.currency ?? "AED";
const OTHER_CCY = CCY === "USD" ? "EUR" : "USD";

const capture = async (companyName, status, industry, companyCountry, companyCity) =>
  (await api(`/projects/${PROJECT}/triage/capture`, { method: "POST", token, body: { companyName, status, industry, companyCountry, companyCity, source: "manual" } })).body.id;
const AURORA = await capture("Aurora Holdings", "inUniverse", "Financial Services", "United Arab Emirates", "Dubai");
const BASALT = await capture("Basalt Mining", "shortlisted", "Mining & Metals", "Saudi Arabia", "Riyadh");
await capture("Cedar Retail", "inUniverse", "Retail", "Kuwait", "Kuwait City");
const DELTA = await capture("Delta Contracting", "declined", "Construction", "Bahrain", "Manama");
const COMPANIES_SEEDED = 4;

const EXECUTIVES = [
  { triageCompanyId: AURORA, fullName: "Fatima Al Mansoori", title: "Group CFO", seniority: "C-Suite", gender: "female", nationality: "Emirati",
    locationCountry: "United Arab Emirates", locationCity: "Dubai", compensation: { currency: CCY, baseSalary: 1000000, allowances: 100000, bonus: 200000 } },
  { triageCompanyId: AURORA, fullName: "Karim Mostafa", title: "Financial Controller", seniority: "N-1", gender: "male", nationality: "Egyptian",
    locationCountry: "United Arab Emirates", locationCity: "Dubai", compensation: { currency: CCY, baseSalary: 500000 } },
  { triageCompanyId: BASALT, fullName: "Rana Haddad", title: "Chief Financial Officer", seniority: "C-Suite", gender: "other", nationality: "Arab expat, non-GCC",
    locationCountry: "Saudi Arabia", locationCity: "Riyadh", compensation: { currency: OTHER_CCY, baseSalary: 300000 } },
  { triageCompanyId: BASALT, fullName: "James Whitfield", title: "Head of Treasury", seniority: "N-1", nationality: "British" },
  { triageCompanyId: DELTA, fullName: "Noura Al Saud", title: "Chief Financial Officer", seniority: "C-Suite", gender: "female", nationality: "Saudi" },
];
for (const executive of EXECUTIVES) {
  const added = await api(`/projects/${PROJECT}/candidates`, { method: "POST", token, body: { ...executive, source: "manual" } });
  if (added.status !== 201) throw new Error(`seeding ${executive.fullName} answered ${added.status}: ${JSON.stringify(added.body)}`);
}

// Audit rows land asynchronously; the activity case reads them, so wait for the last one.
for (let i = 0; i < 50 && num(`SELECT count(*) FROM app_lm_audit_event WHERE target_id = '${PROJECT}' AND event_type = 'CANDIDATE_ADDED'`) < EXECUTIVES.length; i++) {
  await new Promise((resolve) => setTimeout(resolve, 200));
}

const REPORT = (await api(`/projects/${PROJECT}/report`, { token })).body;
const REPORTS_URL = (chapter) => `${WEB}/projects/${PROJECT}/reports${chapter ? `?chapter=${chapter}` : ""}`;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
const shot = (name, target = page) => target.screenshot({ path: join(SHOTS, `reports-${name}.png`) });

async function signIn(target, email) {
  await target.goto(`${WEB}/login`);
  await target.getByPlaceholder("you@firm.com").fill(email);
  await target.getByPlaceholder("••••••••").fill(PASSWORD);
  await target.getByRole("button", { name: "Continue", exact: true }).click();
  await target.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
}

/**
 * A KPI tile is its label over its figure over a sub-line. The label is upper-cased by CSS, which
 * innerText reports, so the tile is found by its source text and read as figure + sub. The figure
 * counts up, so it is polled until it settles on `figure`.
 */
const tile = (target, label) => target.locator(`xpath=//div[normalize-space(text())=${JSON.stringify(label)}]/..`).first();
async function tileRead(target, label, figure, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  let read = { figure: "", sub: "" };
  while (Date.now() < deadline) {
    const parts = await tile(target, label).locator(":scope > div").allInnerTexts().catch(() => []);
    read = { figure: (parts[1] ?? "").replace(/\s+/g, ""), sub: (parts[2] ?? "").replace(/\s+/g, " ").trim() };
    if (read.figure === figure) return read;
    await target.waitForTimeout(250);
  }
  return read;
}
const chapterHeading = (target) => target.locator("section h1").first();
const benchmark = (target, name) => target.getByText(`Cross-mandate ${name} benchmark — not built.`, { exact: true });

try {
  section("R1  the talent mapping report");
  await step("R1.1", "the lead signs in and opens the Reports tab", async () => {
    await signIn(page, EMAIL);
    await page.goto(REPORTS_URL());
    await chapterHeading(page).waitFor({ timeout: 25000 });
    check("R1.1", "the Reports tab opens on the Mapping progress chapter", "Are we going to hit the deadline?", await chapterHeading(page).innerText());
  });

  // ---------------------------------------------------------------- the chapters
  const CHAPTERS = [
    ["progress", "Mapping progress", "Are we going to hit the deadline?"],
    ["market", "Shape of the market", "Where does the universe actually sit?"],
    ["comp", "Remuneration", "Are we underpaying — against real evidence, not an estimate?"],
    ["dei", "Diversity & DEI", "What does the mapped talent pool actually look like?"],
  ];
  await step("R1.2", "the step rail walks all four chapters, the URL following", async () => {
    const seen = [];
    for (const [key, label, question] of CHAPTERS) {
      await page.getByRole("navigation", { name: "Report chapters" }).getByRole("link", { name: label }).click();
      await page.locator("section h1", { hasText: question }).waitFor({ timeout: 10000 });
      seen.push(`${new URL(page.url()).searchParams.get("chapter")}:${(await page.getByRole("navigation", { name: "Report chapters" }).locator('[aria-current="page"]').innerText()).trim()}`);
    }
    check("R1.2", "progress → market → comp → dei, each marked active and in ?chapter=",
      CHAPTERS.map(([key, label]) => `${key}:${label}`).join(" "), seen.join(" "));
  });

  await step("R1.3", "Mapping progress states the report's own coverage", async () => {
    await page.goto(REPORTS_URL("progress"));
    const covered = REPORT.progress.companiesCumulative.at(-1);
    const target = REPORT.progress.targetCompanies;
    check("R1.3a", `Companies mapped reads ${covered}/${target}`, `${covered}/${target}`,
      (await tileRead(page, "Companies mapped", `${covered}/${target}`)).figure);
    const executives = REPORT.head.executivesMapped;
    check("R1.3b", `Executives identified reads ${executives}`, `${executives}`,
      (await tileRead(page, "Executives identified", `${executives}`)).figure);
    check("R1.3c", "the seed is what the report counts", `${EXECUTIVES.length}|3`, `${executives}|${REPORT.head.universeCount}`);
    await shot("progress");
  });

  await step("R1.4", "Researcher performance is drawn for the lead", async () => {
    const card = page.getByText("Researcher performance", { exact: true }).first();
    await card.waitFor({ timeout: 10000 });
    await card.scrollIntoViewIfNeeded();
    pass("R1.4a", "the Researcher performance card is on the progress chapter for the LEAD");
    const team = await api(`/projects/${PROJECT}/report/team`, { token });
    check("R1.4b", "GET /report/team credits the lead with every executive", `200|${LEAD_NAME}:${EXECUTIVES.length}`,
      `${team.status}|${(team.body?.researchers ?? []).map((r) => `${r.name}:${r.executives}`).join(",")}`);
    await page.getByText(LEAD_NAME).first().waitFor({ timeout: 10000 });
    pass("R1.4c", "…and names the lead in it");
    await shot("researcher-performance");
  });

  await step("R1.5", "Shape of the market places the report's executives", async () => {
    await page.goto(REPORTS_URL("market"));
    await chapterHeading(page).waitFor({ timeout: 15000 });
    const placed = REPORT.market.cells.reduce((sum, cell) => sum + cell.count, 0);
    const mapped = REPORT.head.executivesMapped;
    check("R1.5a", `Placed in the matrix reads ${placed}/${mapped}`, `${placed}/${mapped}`,
      (await tileRead(page, "Placed in the matrix", `${placed}/${mapped}`)).figure);
    const board = REPORT.market.cells.filter((cell) => cell.level === "Board").reduce((sum, cell) => sum + cell.count, 0);
    const boardTile = await tileRead(page, "Board-level total", `${board}`);
    check("R1.5b", `Board-level total reads ${board} across ${REPORT.market.sectors.length} sectors`,
      `${board}|across all ${REPORT.market.sectors.length} sectors`, `${boardTile.figure}|${boardTile.sub}`);
    await shot("market");
  });

  await step("R1.6", "Remuneration counts the disclosures and the other-currency packages", async () => {
    await page.goto(REPORTS_URL("comp"));
    await chapterHeading(page).waitFor({ timeout: 15000 });
    const disclosed = REPORT.remuneration.disclosures.length;
    const other = REPORT.remuneration.otherCurrency;
    const packages = await tileRead(page, "Disclosed packages", `${disclosed}`);
    check("R1.6a", `Disclosed packages reads ${disclosed}`, `${disclosed}`, packages.figure);
    check("R1.6b", `…in ${REPORT.remuneration.currency}, with ${other} in another currency counted, not converted`,
      `in ${REPORT.remuneration.currency} · ${other} in other currencies not shown`, packages.sub);
    check("R1.6c", "the seed is what the report counts", "2|1", `${disclosed}|${other}`);
    await benchmark(page, "compensation").waitFor({ timeout: 5000 });
    pass("R1.6d", "the cross-mandate compensation benchmark is marked not built");
    await shot("comp");
  });

  await step("R1.7", "Diversity divides by the recorded, not the headcount", async () => {
    await page.goto(REPORTS_URL("dei"));
    await chapterHeading(page).waitFor({ timeout: 15000 });
    const d = REPORT.diversity;
    const sum = (key) => d.genderByLevel.reduce((total, row) => total + row[key], 0) + (d.genderWithoutLevel?.[key] ?? 0);
    const female = sum("female");
    const recorded = female + sum("male") + sum("other");
    const femaleTile = await tileRead(page, "Female, overall", `${Math.round((female / recorded) * 100)}%`);
    check("R1.7a", `Female, overall is ${female} of ${recorded} recorded`, `${Math.round((female / recorded) * 100)}%|${female} of ${recorded} recorded`,
      `${femaleTile.figure}|${femaleTile.sub}`);
    check("R1.7b", "the one executive with no gender is outside the denominator", `${EXECUTIVES.length - 1}|${d.genderUnrecorded}`,
      `${recorded}|1`);
    const groups = d.nationalities.filter((row) => row.total > 0).length;
    check("R1.7c", `Nationalities represented reads ${groups}`, `${groups}`,
      (await tileRead(page, "Nationalities represented", `${groups}`)).figure);
    await benchmark(page, "diversity").waitFor({ timeout: 5000 });
    pass("R1.7d", "the cross-mandate diversity benchmark is marked not built");
    await shot("dei");
  });

  // ---------------------------------------------------------------- the client seat
  await step("R1.8", "a pure client seat reads the report without the researcher breakdown", async () => {
    check("R1.8a", "GET /report is open to the client seat (WORK_VIEW)", 200, (await api(`/projects/${PROJECT}/report`, { token: repToken })).status);
    check("R1.8b", "GET /report/team is refused to the client seat", 403, (await api(`/projects/${PROJECT}/report/team`, { token: repToken })).status);
    const repContext = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const repPage = await repContext.newPage();
    try {
      await signIn(repPage, REP_EMAIL);
      await repPage.goto(REPORTS_URL("progress"));
      await chapterHeading(repPage).waitFor({ timeout: 25000 });
      await tileRead(repPage, "Companies mapped", `${REPORT.progress.companiesCumulative.at(-1)}/${REPORT.progress.targetCompanies}`);
      await repPage.getByText("Recent momentum", { exact: true }).waitFor({ timeout: 10000 });
      check("R1.8c", "the client's progress chapter has no Researcher performance", 0,
        await repPage.getByText("Researcher performance", { exact: true }).count());
      await shot("client-progress", repPage);
    } finally {
      await repContext.close();
    }
  });

  // ---------------------------------------------------------------- the side panel
  await step("R1.9", "a projects-list row opens the side panel with progress and phrased activity", async () => {
    const project = (await api("/projects", { token })).body.find((p) => p.id === PROJECT);
    await page.goto(`${WEB}/`);
    await page.locator('[role="row"]').filter({ hasText: TITLE }).first().click();
    const drawer = page.getByRole("dialog", { name: new RegExp(TITLE) });
    await drawer.waitFor({ timeout: 10000 });
    const progress = (await drawer.innerText()).replace(/\s+/g, " ");
    checkHas("R1.9a", `Mapping progress reads ${project.mappedCompanies} of ${project.companies} companies`,
      `${project.mappedCompanies} of ${project.companies} companies with an executive mapped`, progress);
    check("R1.9aa", "the progress bar is the same share", `${Math.round((project.mappedCompanies / project.companies) * 100)}`,
      await drawer.getByRole("progressbar", { name: "Universe companies with an executive mapped" }).getAttribute("aria-valuenow"));
    check("R1.9b", "the seed is what the list counts", "2|3", `${project.mappedCompanies}|${project.companies}`);
    const lines = drawer.locator("ul li");
    await lines.first().waitFor({ timeout: 10000 });
    const texts = (await lines.allInnerTexts()).map((text) => text.split("\n")[0].trim());
    const first = LEAD_NAME.split(" ")[0];
    check("R1.9c", "the executives read as one merged line", true, texts.includes(`${first} mapped ${EXECUTIVES.length} executives`));
    check("R1.9d", "the typed-in companies read as one merged line", true, texts.includes(`${first} added ${COMPANIES_SEEDED} companies`));
    check("R1.9e", "the mandate's creation is a line of its own", true, texts.includes(`${first} created the position`));
    note("R1.9f", `activity lines: ${texts.join(" | ")}`);
    await shot("drawer");
  });

  // ---------------------------------------------------------------- the map toggle
  await step("R1.10", "the In-universe page offers Table | Map only where Mapbox is configured", async () => {
    const config = (await api("/talent-map/config", { token })).body;
    await page.goto(`${WEB}/projects/${PROJECT}/companies/universe`);
    await page.getByText("Aurora Holdings").first().waitFor({ timeout: 25000 });
    const toggles = await page.getByRole("radiogroup", { name: "View" }).count();
    check("R1.10", `with Mapbox ${config?.enabled ? "configured" : "unconfigured"}, the Table | Map toggle is ${config?.enabled ? "present" : "absent"}`,
      config?.enabled ? 1 : 0, toggles);
    await shot("universe");
  });
} finally {
  await browser.close();
  console.log(`\n\x1b[1;36m---- reports.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);
  process.exitCode = failed === 0 ? 0 : 1;
}
