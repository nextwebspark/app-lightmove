// Strategy → company search, in a real browser.
//
// 14-strategy-company-search.sh proves the HTTP contract. This proves the things only the screen can:
// that a chip click reaches the stored filter and the table agrees with it, that the rail's counts are
// the ones the database holds, that LIKE metacharacters stay literal all the way from the keyboard,
// that a sort header cycles both directions, and that a saved search captures the filter the user was
// actually looking at.
//
// Playwright is imported as a bare specifier — a devDependency of apps/web hoisted to the repo root,
// which Node's resolver finds by walking up from e2e/. Run it from e2e/ (`node spa/strategy.mjs`).
//
// Every expected value is read from the database at run time. The universe is ETL-owned and reloads
// wholesale, so a hard-coded count would go red on the next pipeline load with nothing broken.

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

const EMPTY_FILTER = {
  industries: [], marketSegments: [], countries: [],
  employeeBands: [], revenueBands: [], employeeRange: null, revenueRange: null,
};

// --- the cast ---------------------------------------------------------------

const UNIVERSE = num("SELECT count(*) FROM app_lm_apollo_companies");
if (UNIVERSE < 100) {
  // Skipped, not failed — see 14-strategy-company-search.sh. The universe needs gcloud to pull and CI
  // has none, so a red run here would be the harness reporting its own environment, nightly.
  console.log(`\n\x1b[2mSKIP    the Apollo universe holds ${UNIVERSE} rows — run \`npm run dev:db:apollo\` to run these cases\x1b[0m`);
  record("S0", "SKIP", "the Apollo universe is not loaded");
  process.exit(0);
}

const EMAIL = `lm-e2e-strategy-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Lena Lead", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await api("/auth/verify", { method: "POST", body: { token: linkFor(EMAIL, "verify").split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { name: `Strategy SPA ${STAMP}`, companySize: "11-50 people", primaryRegion: "GCC", teamFocus: "Executive search" } });
// A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: "Gulf Aviation Holding", customDomain: "gulfaviation.example", sector: "aviation", hqCountry: "United Arab Emirates" } })).body.id;
const PROJECT = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Technology Officer" } })).body.id;
const STRATEGY_URL = `${WEB}/projects/${PROJECT}/strategy`;

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1680, height: 1050 } });
const page = await context.newPage();
let facets = null;
page.on("response", async (response) => {
  if (!response.url().includes("/companies/facets") || !response.ok()) return;
  // Never assign the failure: a body that cannot be read (the page navigated away mid-flight) used to
  // overwrite an already-captured payload with null, and S2.2 then reported the counts as never seen.
  const payload = await response.json().catch(() => null);
  if (payload) facets = payload;
});

/** The rail's counts arrive on their own request, so reading the variable straight away races it. */
async function waitForFacets(timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (!facets && Date.now() < deadline) await page.waitForTimeout(200);
  return facets;
}

const shot = (name) => page.screenshot({ path: join(SHOTS, `strategy-${name}.png`) });
const countBar = () => page.locator('text=/(\\d[\\d,]* - \\d[\\d,]* of [\\d,]+|0 results)/').first();
const barText = () => countBar().innerText();
async function total() {
  const text = await barText();
  return text.includes("0 results") ? 0 : Number(text.split(" of ")[1].replace(/,/g, ""));
}
/** Poll until the bar reports `want`. The filter autosaves on a debounce, so this is never instant. */
async function waitTotal(want, timeoutMs = 25000) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    try { last = await total(); if (last === want) return last; } catch { /* mid re-render */ }
    await page.waitForTimeout(200);
  }
  return last;
}
// The table is built from ARIA roles on divs, not a <table>: the header is the first role="row".
const allRows = () => page.locator('[role="table"] [role="row"]');
const cellsOf = (index) => allRows().nth(index + 1).locator('[role="cell"]').allInnerTexts();
const rowCount = async () => (await allRows().count()) - 1;
const columnValues = async (column) => {
  const out = [];
  for (let i = 0, n = await rowCount(); i < n; i++) out.push((await cellsOf(i))[column]);
  return out;
};
// Location starts open, so a blind click on its header would close it.
const openAccordion = async (label) => {
  const header = page.getByRole("button", { name: new RegExp(`^${label}`) }).first();
  if ((await header.getAttribute("aria-expanded")) !== "true") await header.click();
  await page.waitForTimeout(350);
};
// The header div also carries role=button, so Reset has to be matched as a real <button>.
const resetButton = () => page.locator('[aria-label="Filters"] button').filter({ hasText: /^Reset$/ }).first();
const sortHeader = (name) => page.locator('[role="columnheader"] button').filter({ hasText: new RegExp(`^${name}`, "i") }).first();
const toastText = async () => (await page.locator('[role="status"]').first().innerText().catch(() => "(no toast)")).trim();

/** Wipe the mandate's saved filter server-side and reload, so every case starts at the whole universe. */
async function freshFilter() {
  await api(`/projects/${PROJECT}/strategy/filter`, { method: "PUT", token, body: { filter: EMPTY_FILTER } });
  await api(`/projects/${PROJECT}/strategy/off-limits`, { method: "PUT", token, body: { apolloAccountIds: [] } });
  await page.goto(STRATEGY_URL);
  await page.waitForSelector('[role="table"]', { timeout: 25000 });
  await waitTotal(UNIVERSE);
}

const TOP_COUNTRY = sql("SELECT company_country FROM app_lm_apollo_companies WHERE company_country IS NOT NULL GROUP BY 1 ORDER BY count(*) DESC LIMIT 1");
const SECOND_COUNTRY = sql("SELECT company_country FROM app_lm_apollo_companies WHERE company_country IS NOT NULL GROUP BY 1 ORDER BY count(*) DESC OFFSET 1 LIMIT 1");

try {
  // ---------------------------------------------------------------- S1 access
  section("S1  the route guard, login and session restore");
  await page.goto(STRATEGY_URL);
  await page.waitForURL(/\/login/, { timeout: 15000 });
  pass("S1.1", "an unauthenticated deep link is bounced to /login");
  await page.getByPlaceholder("you@firm.com").fill(EMAIL);
  await page.getByPlaceholder("••••••••").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
  await freshFilter();
  await page.reload();
  await page.waitForSelector('[role="table"]', { timeout: 25000 });
  check("S1.2", "a cold reload rebuilds the session from the refresh cookie alone", false, page.url().includes("/login"));

  // ---------------------------------------------------------------- S2 facets
  section("S2  the filter rail's counts are the database's");
  check("S2.1", "an untouched filter is the whole universe", UNIVERSE, await waitTotal(UNIVERSE));
  await shot("initial");
  // Four cases hang off this branch, so a race here silently skips S2.3-S2.5 as well as failing S2.2.
  if (!(await waitForFacets())) fail("S2.2", "the rail's facet counts loaded", "no /companies/facets response seen");
  else {
    const sum = (axis) => facets[axis].reduce((total, entry) => total + entry.count, 0);
    check("S2.2", "the headcount bands account for every company", UNIVERSE, sum("employeeBands"));
    check("S2.3", "the revenue bands, Unknown included, account for every company", UNIVERSE, sum("revenueBands"));
    // Not asserted — see 14.4 and issue #91: companies with no industry fall outside every sector
    // group, so this sum is short by exactly those rows. Printed, not failed.
    note("S2.4", `sector groups sum to ${facets.sectorGroups.reduce((total, group) => total + group.count, 0).toLocaleString()} of ${UNIVERSE.toLocaleString()} — the rest carry no industry (#91)`);
    check("S2.5", "Unknown revenue is the rows carrying no figure",
      num("SELECT count(*) FROM app_lm_apollo_companies WHERE annual_revenue IS NULL"),
      facets.revenueBands.find((band) => band.value === "unknown")?.count);
    check("S2.6", `the ${TOP_COUNTRY} chip counts what the database holds`,
      num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${TOP_COUNTRY.replace(/'/g, "''")}'`),
      facets.countries.find((country) => country.value === TOP_COUNTRY)?.count);
    note("S2.7", `market segments overlap and sum to ${sum("marketSegments").toLocaleString()} over ${UNIVERSE.toLocaleString()} rows`);
  }

  // ---------------------------------------------------------------- S3 filtering
  section("S3  a chip click reaches the stored filter, and the table agrees");
  await step("S3.1", "one Location chip narrows to that country", async () => {
    await freshFilter();
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${TOP_COUNTRY}`) }).first().click();
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${TOP_COUNTRY.replace(/'/g, "''")}'`);
    check("S3.1", "one Location chip narrows to that country", want, await waitTotal(want));
    await shot("one-country");
  });
  await step("S3.2", "two chips on one axis are OR-ed", async () => {
    await page.getByRole("button", { name: new RegExp(`^${SECOND_COUNTRY}`) }).first().click();
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country IN ('${TOP_COUNTRY.replace(/'/g, "''")}','${SECOND_COUNTRY.replace(/'/g, "''")}')`);
    check("S3.2", "two chips on one axis are OR-ed", want, await waitTotal(want));
  });
  await step("S3.3", "Reset clears the axis", async () => {
    await resetButton().click();
    check("S3.3", "Reset clears the axis", UNIVERSE, await waitTotal(UNIVERSE));
  });
  await step("S3.4", "a headcount band filters by its own bounds", async () => {
    await freshFilter();
    await openAccordion("# Employees");
    await page.getByText("1001-2000", { exact: true }).first().click();
    const want = num("SELECT count(*) FROM app_lm_apollo_companies WHERE num_employees BETWEEN 1001 AND 2000");
    check("S3.4", "a headcount band filters by its own bounds", want, await waitTotal(want));
  });
  await step("S3.5", "a custom range wins outright over the ticked band", async () => {
    await page.getByRole("radio", { name: "Custom Range" }).first().click();
    await page.waitForTimeout(300);
    await page.getByPlaceholder("Min").first().fill("1500");
    await page.getByPlaceholder("Max").first().fill("1600");
    const want = num("SELECT count(*) FROM app_lm_apollo_companies WHERE num_employees BETWEEN 1500 AND 1600");
    check("S3.5", "a custom range wins outright over the ticked band", want, await waitTotal(want));
    await shot("custom-range");
    // activeAxisCount counts the five list axes and neither range, so this reads 0 — see the UAT report.
    const badge = await page.locator("button[aria-expanded] span.bg-sky-dim").first().innerText().catch(() => "?");
    note("S3.5b", `the "Show Filters" badge reads "${badge}" while a custom range is the only filter in force`);
  });
  await step("S3.6", "switching back to Predefined clears the typed range", async () => {
    await page.getByRole("radio", { name: "Predefined Range" }).first().click();
    check("S3.6", "switching back to Predefined clears the typed range", UNIVERSE, await waitTotal(UNIVERSE));
  });
  await step("S3.7", "Revenue → Unknown reaches the rows with no figure", async () => {
    await freshFilter();
    await openAccordion("Revenue");
    await page.getByText("Unknown", { exact: true }).first().click();
    const want = num("SELECT count(*) FROM app_lm_apollo_companies WHERE annual_revenue IS NULL");
    check("S3.7", "Revenue → Unknown reaches the rows with no figure", want, await waitTotal(want));
    await shot("revenue-unknown");
  });
  await step("S3.8", "a whole sector takes all of its industries", async () => {
    await freshFilter();
    await openAccordion("Industry");
    // Sector and industry rows are role=checkbox, not buttons, and the first group is whatever the
    // taxonomy file lists first — read it from the facets rather than naming it here.
    const group = facets.sectorGroups[0];
    const industries = group.industries.map((entry) => `'${entry.value.replace(/'/g, "''")}'`).join(",");
    await page.getByRole("checkbox", { name: new RegExp(`^${group.name}`) }).first().click();
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE lower(industry) IN (${industries})`);
    check("S3.8", `selecting the ${group.name} sector takes all ${group.industries.length} of its industries`, want, await waitTotal(want));
    await shot("sector");
  });
  await step("S3.9", "two different axes are AND-ed", async () => {
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${TOP_COUNTRY}`) }).first().click();
    const group = facets.sectorGroups[0];
    const industries = group.industries.map((entry) => `'${entry.value.replace(/'/g, "''")}'`).join(",");
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE lower(industry) IN (${industries}) AND company_country = '${TOP_COUNTRY.replace(/'/g, "''")}'`);
    check("S3.9", "two different axes are AND-ed", want, await waitTotal(want));
    await shot("two-axes");
  });

  // ---------------------------------------------------------------- S4 search
  section("S4  the name box, and the wildcards that must stay literal");
  await step("S4.1", "the name box narrows within the saved filter", async () => {
    await freshFilter();
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${SECOND_COUNTRY}`) }).first().click();
    const country = SECOND_COUNTRY.replace(/'/g, "''");
    await waitTotal(num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${country}'`));
    await page.getByPlaceholder("Search companies...").fill("a");
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${country}' AND company_name ILIKE '%a%'`);
    check("S4.1", "the name box narrows within the saved filter", want, await waitTotal(want));
  });
  await step("S4.2", "'%' is a literal, not a wildcard", async () => {
    const country = SECOND_COUNTRY.replace(/'/g, "''");
    await page.getByPlaceholder("Search companies...").fill("%");
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${country}' AND company_name LIKE '%\\%%'`);
    check("S4.2", "'%' is matched literally — a one-character search must not return everything", want, await waitTotal(want));
    await shot("percent-literal");
  });
  await step("S4.3", "'_' is a literal, not a wildcard", async () => {
    const country = SECOND_COUNTRY.replace(/'/g, "''");
    await page.getByPlaceholder("Search companies...").fill("_");
    const want = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${country}' AND company_name LIKE '%\\_%'`);
    check("S4.3", "'_' is matched literally", want, await waitTotal(want));
  });
  await step("S4.4", "a search matching nothing says so", async () => {
    await page.getByPlaceholder("Search companies...").fill("zzzznotacompanyname");
    check("S4.4", "a search matching nothing reports 0 results", 0, await waitTotal(0));
    await shot("no-match");
  });

  // ---------------------------------------------------------------- S5 sorting
  section("S5  sorting runs on the server, in both directions");
  await step("S5.1", "the Company header sorts alphabetically", async () => {
    await freshFilter();
    await sortHeader("COMPANY").click();
    await page.waitForTimeout(2000);
    const names = await columnValues(0);
    // Expected comes from the database, not from JS. localeCompare is ICU and Postgres sorts in its
    // own collation; the two disagree on punctuation, so a name beginning with ' or " or # made this
    // fail against a sort that was correct. Every other case in this file reads psql for the same
    // reason — the harness must not hold an opinion the server has never agreed to.
    const expected = execFileSync("psql", [PG_URL, "-Atc",
      "SELECT company_name FROM app_lm_apollo_companies ORDER BY company_name ASC NULLS LAST, apollo_account_id LIMIT 5"])
      .toString().trim().split("\n");
    check("S5.1", "name ascending really is alphabetical", JSON.stringify(expected), JSON.stringify(names.slice(0, 5)));
  });
  await step("S5.2", "an ascending revenue sort buries the blanks", async () => {
    await freshFilter();
    const header = sortHeader("REVENUE");
    await header.click(); await page.waitForTimeout(1800);   // descending
    const descending = await columnValues(4);
    await header.click(); await page.waitForTimeout(1800);   // ascending
    const ascending = await columnValues(4);
    check("S5.2a", "descending revenue opens on real figures", false, descending[0] === "—");
    // Apollo publishes a figure on one row in ten; without NULLS LAST this is nine pages of blanks.
    check("S5.2b", "ascending revenue does not open on the nine-in-ten blank rows", false, ascending[0] === "—");
    note("S5.2c", `revenue ascending opens on ${ascending.slice(0, 3).join(", ")}`);
    await shot("sort-revenue-asc");
  });
  await step("S5.3", "a sort change returns to page 1", async () => {
    await freshFilter();
    await page.getByRole("button", { name: "Next page" }).click();
    await page.waitForTimeout(1500);
    const onPageTwo = (await barText()).startsWith("26");
    await sortHeader("COMPANY").click();
    await page.waitForTimeout(2000);
    check("S5.3", "changing the sort returns to page 1", true, onPageTwo && (await barText()).startsWith("1 - 25"));
  });

  // ---------------------------------------------------------------- S6 paging
  section("S6  paging");
  await step("S6.1", "paging through a filtered result", async () => {
    await freshFilter();
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${SECOND_COUNTRY}`) }).first().click();
    await waitTotal(num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${SECOND_COUNTRY.replace(/'/g, "''")}'`));
    check("S6.1a", "Previous is disabled on page 1", true, await page.getByRole("button", { name: "Previous page" }).isDisabled());
    await page.getByRole("button", { name: "Next page" }).click();
    await page.waitForTimeout(1500);
    check("S6.1b", "page 2 counts from 26", true, (await barText()).startsWith("26 - 50 of"));
    await shot("page-two");
  });
  await step("S6.2", "a filter change deep in the pages returns to page 1", async () => {
    await page.getByRole("button", { name: "Next page" }).click(); await page.waitForTimeout(1200);
    await page.getByRole("button", { name: "Next page" }).click(); await page.waitForTimeout(1200);
    const deep = (await barText()).startsWith("76 - 100");
    await page.getByRole("button", { name: new RegExp(`^${TOP_COUNTRY}`) }).first().click();
    await page.waitForTimeout(2500);
    // Staying on page 4 of a filter that now matches two companies shows an empty table over a
    // non-empty result, which is the bug the page reset exists to prevent.
    check("S6.2", "adding a country while on page 4 returns to page 1", true, deep && (await barText()).startsWith("1 - 25 of"));
  });

  // ---------------------------------------------------------------- S7 off-limits
  section("S7  off-limits");
  await step("S7.1", "barring a company removes it from the results", async () => {
    await freshFilter();
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${SECOND_COUNTRY}`) }).first().click();
    const scope = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${SECOND_COUNTRY.replace(/'/g, "''")}'`);
    await waitTotal(scope);
    const target = (await cellsOf(0))[0].split("\n").pop();
    await openAccordion("Off-limits");
    await page.getByPlaceholder(/search/i).last().fill(target.slice(0, 14));
    await page.waitForTimeout(1200);
    await page.locator('[role="option"], li, button').filter({ hasText: target.slice(0, 14) }).first().click();
    check("S7.1a", "the barred company drops out of the count", scope - 1, await waitTotal(scope - 1));
    check("S7.1b", "…and never appears in the page", false, (await columnValues(0)).some((name) => name.endsWith(target)));
    await shot("off-limits");
    // The picker clears its input on a pick but never closes its list, and keepPreviousData holds the
    // old rows, so the dropdown stays open over the EXCLUDED chips it just added to — see the UAT
    // report. Asserted rather than worked around: this is what a consultant sees.
    check("S7.1c", "the suggestion list closes once a company is picked", "false",
      String(await page.locator('[aria-label="Search companies"]').last().getAttribute("aria-expanded")));
    await page.keyboard.press("Escape");
    await page.waitForTimeout(300);
    // Scoped to the rail: "Remove" is a common label elsewhere in the shell.
    const chip = page.locator('[aria-label="Filters"] [aria-label^="Remove "]').first();
    if (!(await chip.isVisible().catch(() => false))) await openAccordion("Off-limits");
    await chip.waitFor({ state: "visible", timeout: 15000 });
    await chip.click();
    check("S7.1d", "removing the exclusion restores it", scope, await waitTotal(scope));
  });

  // ---------------------------------------------------------------- S8 saved searches
  section("S8  saved searches");
  await step("S8.1", "saving, loading and deleting a search", async () => {
    await freshFilter();
    await openAccordion("Location");
    await page.getByRole("button", { name: new RegExp(`^${TOP_COUNTRY}`) }).first().click();
    const scope = num(`SELECT count(*) FROM app_lm_apollo_companies WHERE company_country = '${TOP_COUNTRY.replace(/'/g, "''")}'`);
    await waitTotal(scope);
    await page.getByRole("button", { name: /Save Search/ }).click();
    await page.getByLabel("Name this search").fill("Primary market");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await page.waitForTimeout(1500);
    const saved = (await api(`/projects/${PROJECT}/strategy`, { token })).body.searches.find((entry) => entry.name === "Primary market");
    check("S8.1a", "the search is stored with the filter that was on screen", TOP_COUNTRY, saved?.filter.countries[0]);

    await api(`/projects/${PROJECT}/strategy/filter`, { method: "PUT", token, body: { filter: EMPTY_FILTER } });
    await page.goto(STRATEGY_URL);
    await page.waitForSelector('[role="table"]');
    await waitTotal(UNIVERSE);
    await page.getByRole("button", { name: /Save Search/ }).click();
    await page.waitForTimeout(400);
    await page.locator("button").filter({ hasText: /^Primary market$/ }).first().click();
    check("S8.1b", "loading it re-applies its filter", scope, await waitTotal(scope));
    await shot("saved-search");

    await page.getByRole("button", { name: /Save Search/ }).click();
    await page.waitForTimeout(400);
    await page.getByRole("button", { name: "Delete Primary market" }).click();
    await page.waitForTimeout(1500);
    check("S8.1c", "deleting removes it", 0,
      (await api(`/projects/${PROJECT}/strategy`, { token })).body.searches.filter((entry) => entry.name === "Primary market").length);
  });
  // A regression guard for the race the UAT pass found: the filter autosaves on a 700ms debounce and
  // the save endpoint reads the *stored* filter, so a save inside that window records the scope as it
  // was BEFORE the last chip click — silently, and for every later load of that search. `addAll`
  // already flushes the autosave for exactly this reason; saving a search does not.
  await step("S8.2", "a search saved right after a chip click captures that chip", async () => {
    await freshFilter();
    await openAccordion("Location");
    const started = Date.now();
    await page.getByRole("button", { name: new RegExp(`^${TOP_COUNTRY}`) }).first().click();
    await page.getByRole("button", { name: /Save Search/ }).click();
    await page.getByLabel("Name this search").fill("Fast save");
    await page.getByRole("button", { name: "Save", exact: true }).click();
    const elapsed = Date.now() - started;
    await page.waitForTimeout(2500);
    const saved = (await api(`/projects/${PROJECT}/strategy`, { token })).body.searches.find((entry) => entry.name === "Fast save");
    note("S8.2a", `chip click → Save took ${elapsed}ms; the autosave debounce is 700ms`);
    check("S8.2", "a search saved that fast still captures the chip",
      JSON.stringify([TOP_COUNTRY]), JSON.stringify(saved?.filter.countries ?? null));
    await shot("save-race");
  });

  // ---------------------------------------------------------------- S9 triage hand-off
  section("S9  what the mandate takes from the market");
  await step("S9.1", "the row's + files one company", async () => {
    await freshFilter();
    const before = num(`SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}'`);
    await page.locator('[aria-label^="Add "][aria-label$=" to universe"]').first().click();
    await page.waitForTimeout(1500);
    note("S9.1a", `toast: "${await toastText()}"`);
    check("S9.1", "the row's + files exactly one company", before + 1,
      num(`SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}'`));
    await page.locator('[aria-label^="Add "][aria-label$=" to universe"]').first().click();
    await page.waitForTimeout(1500);
    check("S9.1b", "a second click on the same row does not file it twice", before + 1,
      num(`SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}'`));
  });
  await step("S9.2", "'Add all' over the whole universe is refused, out loud", async () => {
    await freshFilter();
    await page.getByRole("button", { name: /Add all to Universe/ }).click();
    await page.waitForTimeout(2000);
    const toast = await toastText();
    note("S9.2a", `toast: "${toast}"`);
    // The refusal carries both numbers, which is why errorCodes.ts deliberately has no fixed sentence
    // for BULK_ADD_SCOPE_TOO_LARGE — a generic message here would lose them.
    check("S9.2", "the server's own refusal reaches the user with its numbers intact", true,
      /at a time/.test(toast) && new RegExp(UNIVERSE.toLocaleString("en-US")).test(toast));
    await shot("add-all-refused");
  });
  await step("S9.3", "the Triage screen shows what Strategy filed", async () => {
    await page.goto(`${WEB}/projects/${PROJECT}/triage`);
    await page.waitForTimeout(2500);
    const inUniverse = num(`SELECT count(*) FROM app_lm_project_triage_company WHERE project_id = '${PROJECT}' AND status = 'IN_UNIVERSE'`);
    check("S9.3", "the In universe count matches the database", true, (await page.locator("body").innerText()).includes(String(inUniverse)));
    await shot("triage");
  });

  // ---------------------------------------------------------------- S10 columns
  section("S10  the column picker");
  await step("S10.1", "a hidden column stays hidden across a reload", async () => {
    await page.goto(STRATEGY_URL);
    await page.waitForSelector('[role="table"]');
    await page.getByRole("button", { name: /Columns/ }).click();
    await page.waitForTimeout(300);
    await shot("columns");
    await page.getByText("Sector", { exact: true }).first().click();
    await page.keyboard.press("Escape");
    await page.waitForTimeout(500);
    check("S10.1a", "the column disappears", false,
      (await page.locator('[role="columnheader"]').allInnerTexts()).some((header) => /SECTOR/i.test(header)));
    await page.reload();
    await page.waitForSelector('[role="table"]');
    await page.waitForTimeout(1500);
    check("S10.1b", "and is still hidden after a reload", false,
      (await page.locator('[role="columnheader"]').allInnerTexts()).some((header) => /SECTOR/i.test(header)));
  });
} finally {
  await browser.close();
  console.log(`\n\x1b[1;36m---- strategy.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);
  process.exitCode = failed === 0 ? 0 : 1;
}
