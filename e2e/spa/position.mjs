// The Position brief, in a real browser.
//
// 16-position-brief.sh proves the HTTP contract. This proves what only the screen can: that the New
// position modal lands a mandate the list opens, that the role-title combobox offers the template
// library and redrafts the brief from the one picked, that each step's autosave reaches the row, that
// the foot walks the five steps with the URL following, and that publishing turns the rail round to
// "Edit position" and the review's foot towards Strategy.
//
// Run from e2e/ (`node spa/position.mjs`). AI is never touched: no document is attached and no
// "Read from document" / "Extract with AI" is pressed.
//
// Every expected value is read from the API or the database at run time.

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

// --- the cast ---------------------------------------------------------------

const EMAIL = `lm-e2e-position-${STAMP}@${DOMAIN}`;
const REP_EMAIL = `lm-e2e-position-rep-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Pia Position", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await api("/auth/verify", { method: "POST", body: { token: linkFor(EMAIL, "verify").split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { name: `Position SPA ${STAMP}`, companySize: "11-50 people", primaryRegion: "GCC", teamFocus: "Executive search" } });
// A token minted before the workspace existed carries no wsId, so every tenant route 404s until reissued.
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const UNIT = `Gulf Energy Holding ${STAMP}`;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: UNIT, customDomain: `gulfenergy${STAMP}.example`, sector: "energy", hqCountry: "Saudi Arabia" } })).body.id;
// The mandate itself is created through the modal (P1.2), so nothing else exists yet. A title no
// template matches keeps the pick in P1.4 a real redraft rather than a no-op.
const TITLE = `Head of Transformation E2E ${STAMP}`;

const TEMPLATES = (await api("/position-templates", { token })).body;
// The pick is read from the library, not copied: any template carrying a department will do.
const PICK = TEMPLATES.find((template) => template.code === "chief-compliance-officer") ?? TEMPLATES[0];
const PICK_DEPARTMENT = sql(`SELECT coalesce(body->>'department', '') FROM app_lm_position_template
                             WHERE id = '${PICK.id}'`);
const PICK_CRITERIA = num(`SELECT coalesce(jsonb_array_length(body->'criteria'), 0) FROM app_lm_position_template WHERE id = '${PICK.id}'`);

let PROJECT = null;
const positionOf = async () => (await api(`/projects/${PROJECT}/position`, { token })).body;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
const page = await context.newPage();
const shot = (name, target = page) => target.screenshot({ path: join(SHOTS, `position-${name}.png`) });
const briefUrl = (stepKey) => `${WEB}/projects/${PROJECT}${stepKey ? `?step=${stepKey}` : ""}`;
const stepHeading = () => page.locator("h1.type-title").first().innerText();
const railButton = (name) => page.locator("aside").getByRole("button", { name, exact: true });

async function signIn(target, email) {
  await target.goto(`${WEB}/login`);
  await target.getByPlaceholder("you@firm.com").fill(email);
  await target.getByPlaceholder("••••••••").fill(PASSWORD);
  await target.getByRole("button", { name: "Continue", exact: true }).click();
  await target.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
}

async function openBrief(stepKey) {
  await page.goto(briefUrl(stepKey));
  await page.locator("h1.type-title").first().waitFor({ timeout: 25000 });
}

try {
  // ---------------------------------------------------------------- P1.1 access
  section("P1  the Position brief");
  await step("P1.1", "the lead signs in through the real /login page", async () => {
    await signIn(page, EMAIL);
    await page.getByRole("button", { name: "New position" }).first().waitFor({ timeout: 20000 });
    pass("P1.1", "the lead signs in through the real /login page");
  });

  // ---------------------------------------------------------------- P1.2 the modal
  await step("P1.2", "the New position modal creates the mandate", async () => {
    await page.getByRole("button", { name: "New position" }).first().click();
    const modal = page.getByRole("dialog");
    await modal.waitFor();
    await modal.getByPlaceholder("Search or name a new business unit").fill(UNIT.slice(0, 18));
    await modal.getByRole("listbox", { name: "Business units" }).getByRole("option", { name: UNIT }).click();
    await modal.getByPlaceholder("e.g. Chief Financial Officer").fill(TITLE);
    await modal.getByPlaceholder("e.g. Chief Financial Officer").press("Escape").catch(() => {});
    await modal.getByRole("radiogroup", { name: "Project type" }).getByRole("radio", { name: /Search/ }).click();
    await shot("new-modal");
    await modal.getByRole("button", { name: "Create position" }).click();
    await modal.waitFor({ state: "detached", timeout: 15000 });
    const created = (await waitApi("/projects", token, (list) => list.some((p) => p.positionTitle === TITLE)))
      .find((p) => p.positionTitle === TITLE);
    PROJECT = created?.id ?? null;
    check("P1.2", "the modal creates the mandate under the picked unit as a SEARCH",
      `${TITLE}|${clientId}|SEARCH`, `${created?.positionTitle}|${created?.clientId}|${created?.projectType}`);
  });
  if (!PROJECT) throw new Error("no mandate was created — every later case depends on it");

  await step("P1.3", "the list row opens the side panel, and Open position lands on the brief", async () => {
    await page.locator('[role="row"]').filter({ hasText: TITLE }).first().click();
    const drawer = page.getByRole("dialog", { name: new RegExp(TITLE) });
    await drawer.waitFor({ timeout: 10000 });
    await drawer.getByRole("link", { name: /Open position/ }).click();
    await page.waitForURL((url) => url.pathname === `/projects/${PROJECT}`, { timeout: 15000 });
    await page.locator("h1.type-title").first().waitFor({ timeout: 25000 });
    check("P1.3", "Open position lands on the mandate's Role Brief", "Role Brief", await stepHeading());
  });

  // ---------------------------------------------------------------- P1.4 the role title combobox
  await step("P1.4", "the role title type-aheads the template library and redrafts the brief", async () => {
    const box = page.getByRole("combobox", { name: "Role title" });
    await box.fill(PICK.title.split(" ").slice(1).join(" ") || PICK.title);
    const list = page.getByRole("listbox", { name: "Role templates" });
    await list.waitFor({ timeout: 5000 });
    const offered = await list.getByRole("option").allInnerTexts();
    check("P1.4a", `typing offers ${PICK.title} from GET /position-templates`, true,
      offered.some((text) => text.includes(PICK.title)));
    await list.getByRole("option", { name: new RegExp(`^${PICK.title}`) }).first().click();
    const brief = await waitApi(`/projects/${PROJECT}/position`, token,
      (p) => p.details.roleTitle === PICK.title && (p.details.department ?? "") === PICK_DEPARTMENT);
    check("P1.4b", "the brief takes the template's title and department", `${PICK.title}|${PICK_DEPARTMENT}`,
      `${brief.details.roleTitle}|${brief.details.department ?? ""}`);
    check("P1.4c", "…and its screening criteria", PICK_CRITERIA, brief.assessment.criteria.length);
    check("P1.4d", "the screen shows the drafted department", PICK_DEPARTMENT,
      await page.getByRole("textbox", { name: "Department" }).inputValue());
    const project = (await api("/projects", { token })).body.find((p) => p.id === PROJECT);
    check("P1.4e", "picking a template renames the mandate", PICK.title, project?.positionTitle);
    await shot("template-picked");
  });

  await step("P1.5", "a scalar autosaves and survives a reload", async () => {
    const city = `Riyadh ${STAMP.slice(-4)}`;
    await page.getByRole("textbox", { name: "City" }).fill(city);
    const brief = await waitApi(`/projects/${PROJECT}/position`, token, (p) => p.details.locationCity === city);
    check("P1.5a", "the city reaches GET /position through the autosave", city, brief.details.locationCity);
    await page.reload();
    await page.getByRole("textbox", { name: "City" }).waitFor({ timeout: 20000 });
    check("P1.5b", "…and reads back after a reload", city, await page.getByRole("textbox", { name: "City" }).inputValue());
  });

  // ---------------------------------------------------------------- P1.6 walking the steps
  await step("P1.6", "the foot walks every step in order, the URL following", async () => {
    const walk = [
      ["reporting", "Reporting", "Reporting Structure"],
      ["compensation", "Compensation", "Compensation Package"],
      ["assessment", "Assessment Criteria", "Assessment Criteria"],
      ["review", "Review & Publish", "Review & publish"],
    ];
    await openBrief("brief");
    const seen = [];
    for (const [key, name, heading] of walk) {
      await page.getByRole("link", { name: new RegExp(`^Next: ${name.replace("&", "\\&")}`) }).click();
      await page.waitForURL((url) => url.searchParams.get("step") === key, { timeout: 10000 });
      await page.locator("h1.type-title", { hasText: heading }).waitFor({ timeout: 10000 });
      seen.push(`${new URL(page.url()).searchParams.get("step")}:${await stepHeading()}`);
    }
    check("P1.6a", "Next walks brief → reporting → compensation → assessment → review",
      walk.map(([key, , heading]) => `${key}:${heading}`).join(" "), seen.join(" "));
    check("P1.6b", "the review has no Next link", 0, await page.getByRole("link", { name: /^Next:/ }).count());
    await page.getByRole("link", { name: /^Back to Assessment Criteria/ }).click();
    await page.locator("h1.type-title", { hasText: "Assessment Criteria" }).waitFor({ timeout: 10000 });
    check("P1.6c", "Back returns one step", "Assessment Criteria", await stepHeading());
    check("P1.6d", "the rail marks the active step", "Assessment Criteria",
      (await page.getByRole("navigation", { name: "Brief steps" }).locator('[aria-current="page"]').innerText()).trim());
  });

  // ---------------------------------------------------------------- P1.7 reporting
  await step("P1.7", "the org chart draws the mandate seat and takes a new direct report", async () => {
    await openBrief("reporting");
    const canvas = page.getByLabel("Org chart");
    await canvas.waitFor({ timeout: 15000 });
    const seat = canvas.locator(".react-flow__node").filter({ hasText: "This position" });
    await seat.waitFor({ timeout: 10000 });
    check("P1.7a", "the mandate seat carries the role title", true, (await seat.innerText()).includes(PICK.title));

    const before = (await positionOf()).reporting.orgChart;
    const mandate = before.find((node) => node.mandateSeat);
    const reportsBefore = before.filter((node) => node.parentNodeId === mandate.nodeId).length;
    const teamSize = async () => Number((await page.locator("text=/leads \\d+ direct report/").first().innerText()).match(/leads (\d+)/)[1]);
    check("P1.7b", "the team size is the mandate seat's children in the stored chart", reportsBefore, await teamSize());

    await seat.hover();
    await seat.getByRole("button", { name: "Add a direct report" }).click({ force: true });
    // The server drops an unnamed leaf (OrgChartRules.withoutUnnamedLeaves), so a seat only persists
    // once it has a title — the new one is the seat whose title box is still empty.
    const titles = canvas.getByRole("textbox", { name: "Seat title" });
    await page.waitForFunction((count) => document.querySelectorAll('[aria-label="Seat title"]').length === count,
      before.length, { timeout: 5000 });
    let fresh = null;
    for (let i = 0, n = await titles.count(); i < n; i++) if ((await titles.nth(i).inputValue()) === "") fresh = titles.nth(i);
    if (!fresh) throw new Error("no empty seat appeared after Add a direct report");
    await fresh.fill("Head of Regulatory Affairs");
    const after = await waitApi(`/projects/${PROJECT}/position`, token, (p) => p.reporting.orgChart.length === before.length + 1);
    check("P1.7c", "adding a seat stores one more org node", before.length + 1, after.reporting.orgChart.length);
    const reportsAfter = after.reporting.orgChart.filter((node) => node.parentNodeId === mandate.nodeId).length;
    check("P1.7d", "…as a child of the mandate seat", reportsBefore + 1, reportsAfter);
    check("P1.7e", "the team size counts it", reportsAfter, await teamSize());
    await shot("reporting");
  });

  // ---------------------------------------------------------------- P1.8 compensation
  await step("P1.8", "a fixed-amount bonus persists", async () => {
    await openBrief("compensation");
    await page.getByRole("radiogroup", { name: "Bonus basis" }).getByRole("radio", { name: "Fixed amount" }).click();
    const bonus = page.getByRole("textbox", { name: "Bonus target" });
    await bonus.fill("");
    await bonus.pressSequentially("175000");
    await bonus.blur();
    const brief = await waitApi(`/projects/${PROJECT}/position`, token,
      (p) => p.compensation.bonusBasis === "FIXED_AMOUNT" && Number(p.compensation.bonusValue) === 175000);
    check("P1.8", "the bonus is stored as FIXED_AMOUNT 175000", "FIXED_AMOUNT|175000",
      `${brief.compensation.bonusBasis}|${Number(brief.compensation.bonusValue)}`);
    await page.reload();
    await page.getByRole("textbox", { name: "Bonus target" }).waitFor({ timeout: 20000 });
    check("P1.8b", "…and reads back as a fixed amount after a reload", "true",
      await page.getByRole("radiogroup", { name: "Bonus basis" }).getByRole("radio", { name: "Fixed amount" }).getAttribute("aria-checked"));
    await shot("compensation");
  });

  // ---------------------------------------------------------------- P1.9 assessment
  await step("P1.9", "a new criterion autosaves", async () => {
    await openBrief("assessment");
    const text = `Has run a regulator relationship in the GCC ${STAMP.slice(-4)}`;
    const before = (await positionOf()).assessment.criteria.length;
    await page.getByRole("textbox", { name: "Add a criterion" }).fill(text);
    await page.getByRole("textbox", { name: "Add a criterion" }).press("Enter");
    const brief = await waitApi(`/projects/${PROJECT}/position`, token, (p) => p.assessment.criteria.some((c) => c.text === text));
    const saved = brief.assessment.criteria.find((c) => c.text === text);
    check("P1.9a", "the criterion is stored, one more than before", `${before + 1}|MANUAL`,
      `${brief.assessment.criteria.length}|${saved?.source}`);
    await shot("assessment");
  });

  // ---------------------------------------------------------------- P1.10 publish
  await step("P1.10", "publishing turns the rail to Edit position and the foot to Strategy", async () => {
    await openBrief("review");
    check("P1.10a", "an unpublished review offers no Move to Strategy", 0,
      await page.getByRole("button", { name: "Move to Strategy" }).count());
    await railButton("Publish profile").click();
    await railButton("Edit position").waitFor({ timeout: 15000 });
    pass("P1.10b", "after publishing, the rail offers Edit position");
    check("P1.10c", "…and Save draft is disabled on the read-back", true, await railButton("Save draft").isDisabled());
    const brief = await waitApi(`/projects/${PROJECT}/position`, token, (p) => Boolean(p.publication.publishedAt));
    check("P1.10d", "GET /position shows the brief published", true, Boolean(brief.publication.publishedAt));
    check("P1.10e", "the read-back review drops its Edit section links", 0,
      await page.getByRole("button", { name: "Edit section" }).count() + await page.getByRole("link", { name: "Edit section" }).count());
    await shot("published");
    await page.getByRole("button", { name: "Move to Strategy" }).click();
    await page.waitForURL((url) => url.pathname === `/projects/${PROJECT}/strategy`, { timeout: 15000 });
    pass("P1.10f", "Move to Strategy navigates to the mandate's Strategy");
  });

  await step("P1.11", "a published brief reopens on its review", async () => {
    await openBrief();
    check("P1.11", "opening a published brief with no ?step= lands on the review", "Review & publish", await stepHeading());
  });

  // ---------------------------------------------------------------- P1.12 client representative
  await step("P1.12", "a pure client representative reads the brief without the brief's acts", async () => {
    const invited = await api(`/projects/${PROJECT}/representatives/invitations`, {
      method: "POST", token, body: { fullName: "Cleo Client", position: "Group CEO", email: REP_EMAIL },
    });
    if (invited.status >= 300) throw new Error(`invite answered ${invited.status}`);
    const inviteToken = decodeURIComponent(linkFor(REP_EMAIL, "accept-invite").split("token=")[1]);
    const accepted = await api("/onboarding/accept-invitation-signup", {
      method: "POST", body: { token: inviteToken, fullName: "Cleo Client", password: PASSWORD },
    });
    if (accepted.status >= 300) throw new Error(`accept answered ${accepted.status}`);
    const repToken = (await api("/auth/login", { method: "POST", body: { email: REP_EMAIL, password: PASSWORD } })).body.accessToken;
    check("P1.12a", "the representative may read the brief (WORK_VIEW)", 200,
      (await api(`/projects/${PROJECT}/position`, { token: repToken })).status);
    check("P1.12b", "…and may not publish it", 403,
      (await api(`/projects/${PROJECT}/position/publish`, { method: "POST", token: repToken })).status);

    const repContext = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
    const repPage = await repContext.newPage();
    try {
      await signIn(repPage, REP_EMAIL);
      await repPage.goto(`${WEB}/projects/${PROJECT}?step=review`);
      await repPage.locator("h1.type-title").first().waitFor({ timeout: 25000 });
      check("P1.12c", "the client sees the brief to read, not the review to publish", "Position brief",
        await repPage.locator("h1.type-title").first().innerText());
      await repPage.goto(`${WEB}/projects/${PROJECT}?step=brief`);
      await repPage.locator("h1.type-title").first().waitFor({ timeout: 25000 });
      await shot("client-brief", repPage);
      const acts = await repPage.locator("aside").getByRole("button", { name: /^(Publish profile|Publish changes|Edit position)$/ }).count();
      check("P1.12d", "the client's rail offers no Publish / Edit position", 0, acts);
      check("P1.12e", "the client's role title is not editable", false,
        await repPage.getByRole("combobox", { name: "Role title" }).isEditable().catch(() => false));
      // Asking for a live step by URL still reads back: no field on the page takes input.
      check("P1.12e2", "no step URL hands the client a field that takes input", 0,
        await repPage.locator("main input:not([type=hidden]), main textarea, main [contenteditable=true]").count());
      // What a keystroke on that screen does: the server is the fence, so the write must be refused
      // and the stored brief untouched, whatever the screen offered.
      const writes = [];
      repPage.on("response", (response) => {
        if (response.request().method() === "PUT" && response.url().includes("/position/")) writes.push(response.status());
      });
      const storedCity = (await positionOf()).details.locationCity;
      const city = repPage.getByRole("textbox", { name: "City" });
      if (await city.isEditable().catch(() => false)) {
        await city.fill("Typed by a client");
        await repPage.waitForTimeout(3000);
      }
      note("P1.12f", `a client keystroke on the brief sent PUTs answered [${writes.join(", ") || "none"}]`);
      check("P1.12g", "…and the stored brief is untouched", storedCity, (await positionOf()).details.locationCity);
    } finally {
      await repContext.close();
    }
  });
} finally {
  await browser.close();
  console.log(`\n\x1b[1;36m---- position.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);
  process.exitCode = failed === 0 ? 0 : 1;
}
