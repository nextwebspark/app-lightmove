// Consent requests (#1267 phase 2) in a real browser against a real API: an admin turns on consent mode, a colleague
// asks a candidate from the Candidates drawer, the email's link opens the public page with no session, the candidate
// agrees to be kept on file, the drawer says so, and the confirmation's manage link withdraws it. Phase 3: turning
// them visible on the Pipeline asks first, Ask first emails a share request, a yes on their page marks the card
// Consent given, and the position's consent register carries it.
//
// Needs `DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev` (both off by default); mail prints
// to the API console.
//
//   API_LOG=<the API's console log> node e2e/spa/consent-requests.mjs

import { chromium } from "playwright";
import { appendFileSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = process.env.API_LOG ?? join(RUN_DIR, "api.log");
const WEB = process.env.WEB ?? "http://localhost:5173";
const API = process.env.API ?? "http://localhost:8080/api/v1";
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

/** The latest link of this shape printed in an email to this address. */
function linkFor(address, pattern) {
  let mine = false;
  let found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(pattern);
      if (match) found = match[0];
    }
  }
  return found;
}

/** Waits for a link in an email sent after `before` was taken. */
async function nextLinkFor(address, pattern, before) {
  for (let i = 0; i < 40; i++) {
    const found = linkFor(address, pattern);
    if (found && found !== before) return found;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
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

const PRIVACY_LINK = /http:\/\/localhost:5173\/privacy\/[A-Za-z0-9_-]+/;

// --- the seed: an admin, the firm's details, a position and one executive with an address ----------

const EMAIL = `lm-e2e-consent-${STAMP}@${DOMAIN}`;
const NOOR = `noor-${STAMP}@example.com`;
await api("/auth/signup", { method: "POST", body: { fullName: "Cara Consent", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
const verify = linkFor(EMAIL, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
await api("/auth/verify", { method: "POST", body: { token: verify.split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "AGENCY", name: `Consent SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/workspace/candidate-privacy", {
  method: "PUT", token,
  body: {
    controllerName: "Meridian Search Partners FZ-LLC",
    privacyContactEmail: "privacy@meridian.example",
    noticeUrl: "https://meridian.example/privacy",
    defaultLawfulBasis: "legitimate_interest",
  },
});
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Consent Unit ${STAMP}` } })).body.id;
const position = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;
const filed = await api(`/projects/${position}/candidates`, {
  method: "POST", token,
  body: { fullName: "Noor Haddad", title: "Finance Director", linkedinUrl: `https://www.linkedin.com/in/noor-${STAMP}/`, emails: [{ value: NOOR }] },
});
if (filed.status !== 201) throw new Error(`seeding answered ${filed.status}: ${JSON.stringify(filed.body)}`);
const PERSON = filed.body.personId;
const CANDIDATE = filed.body.id;
const CLIENT = `Consent Unit ${STAMP}`;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const shot = (target, name) => target.screenshot({ path: join(SHOTS, `consent-${name}.png`), fullPage: true });

try {
  console.log("\n\x1b[1;36m== CR1  Settings → Data & retention: consent mode\x1b[0m");
  await step("CR1.1", "an admin turns on consent requests and offers career and market updates", async () => {
    await page.goto(`${WEB}/login`);
    await page.getByPlaceholder("you@firm.com").fill(EMAIL);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/settings/data`);
    await page.getByRole("radio", { name: "Notices and consent requests" }).click({ timeout: 15000 });
    await page.getByRole("switch", { name: "Career and market updates" }).click();
    await shot(page, "settings");
    await page.getByRole("button", { name: "Save changes" }).first().click();
    await page.getByText("Candidate privacy saved").first().waitFor({ timeout: 10000 });
    const saved = (await api("/workspace/candidate-privacy", { token })).body;
    check("CR1.1", "consent mode and both purposes are held", "consent|true|true",
      `${saved.privacyMode}|${saved.keepOnFile}|${saved.marketing}`);
  });

  console.log("\n\x1b[1;36m== CR2  Ask for consent from the Candidates drawer\x1b[0m");
  const before = linkFor(NOOR, PRIVACY_LINK);
  await step("CR2.1", "the drawer lists both purposes as not asked, and Ask for consent sends one request", async () => {
    await page.goto(`${WEB}/candidates?person=${PERSON}`);
    const consent = page.getByRole("group", { name: "Consent" });
    await consent.getByText("Never asked").first().waitFor({ timeout: 15000 });
    await shot(page, "drawer-not-asked");
    await consent.getByRole("button", { name: "Ask for consent" }).click();
    const dialog = page.getByRole("dialog", { name: /for consent/ });
    await dialog.getByText("Meridian Search Partners FZ-LLC would like your permission").waitFor();
    await page.waitForTimeout(600);
    await shot(page, "ask-dialog");
    await dialog.getByRole("button", { name: "Send request" }).click();
    await consent.getByText("Asked", { exact: true }).first().waitFor({ timeout: 10000 });
    await shot(page, "drawer-asked");
    const held = (await api(`/candidates/${PERSON}`, { token })).body.privacy.consents;
    check("CR2.1", "both purposes are requested", "requested,requested", held.map((row) => row.status).join(","));
  });

  let requestLink = null;
  await step("CR2.2", "the request went to the person with a link to their page", async () => {
    requestLink = await nextLinkFor(NOOR, PRIVACY_LINK, before);
    check("CR2.2", "the email carries a consent link", true, !!requestLink);
  });

  console.log("\n\x1b[1;36m== CR3  The consent page, no session\x1b[0m");
  const stranger = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const outside = await stranger.newPage();
  const manageBefore = linkFor(NOOR, PRIVACY_LINK);
  await step("CR3.1", "nothing is selected, and agreeing to be kept on file is saved", async () => {
    await outside.goto(requestLink.replace("http://localhost:5173", WEB));
    await outside.getByRole("heading", { name: "Meridian Search Partners FZ-LLC asks for your permission" }).waitFor({ timeout: 15000 });
    const pressed = await outside.getByRole("button", { pressed: true }).count();
    check("CR3.1", "no answer is pre-selected", 0, pressed);
    await shot(outside, "public-request");
    await outside.getByRole("group", { name: "Keep your details on file for future roles" }).getByRole("button", { name: "Yes" }).click();
    await outside.getByRole("group", { name: "Career and market updates" }).getByRole("button", { name: "No" }).click();
    await outside.getByRole("button", { name: "Send my answers" }).click();
    await outside.getByRole("heading", { name: "Thank you, your answers are saved" }).waitFor({ timeout: 10000 });
    await outside.getByText(/^Yes, until/).waitFor({ timeout: 10000 });
    await shot(outside, "public-answered");
  });

  await step("CR3.2", "the drawer shows the consent given until its date, from their page", async () => {
    await page.reload();
    const consent = page.getByRole("group", { name: "Consent" });
    await consent.getByText("Given", { exact: true }).first().waitFor({ timeout: 15000 });
    const line = await consent.getByText(/^Until .* · on their privacy page$/).first().innerText();
    await shot(page, "drawer-given");
    check("CR3.2", "the keep-on-file row says until when, on their page", true, line.startsWith("Until "));
    const declined = await consent.getByText("Declined", { exact: true }).count();
    check("CR3.2b", "career and market updates is declined", 1, declined);
  });

  console.log("\n\x1b[1;36m== CR4  The confirmation's manage link\x1b[0m");
  await step("CR4.1", "the manage link lists the consent in force and withdraws it once confirmed", async () => {
    const manageLink = await nextLinkFor(NOOR, PRIVACY_LINK, manageBefore);
    if (!manageLink) throw new Error("no confirmation with a manage link reached the person");
    await outside.goto(manageLink.replace("http://localhost:5173", WEB));
    await outside.getByRole("heading", { name: "Your consents" }).waitFor({ timeout: 15000 });
    await shot(outside, "public-manage");
    await outside.getByRole("button", { name: "Withdraw" }).click();
    await outside.getByRole("heading", { name: "Withdraw your consent to keep your details on file?" }).waitFor();
    await shot(outside, "public-confirm-withdraw");
    await outside.getByRole("button", { name: "Withdraw consent" }).click();
    await outside.getByRole("heading", { name: "Your consent is withdrawn" }).waitFor({ timeout: 10000 });
    await shot(outside, "public-withdrawn");
    const held = (await api(`/candidates/${PERSON}`, { token })).body.privacy.consents;
    check("CR4.1", "keep on file is withdrawn", "withdrawn",
      held.find((row) => row.purpose === "keep_on_file")?.status);
  });

  await step("CR4.2", "the Declined or withdrawn view lists them", async () => {
    await page.goto(`${WEB}/candidates`);
    await openShowFilter(page);
    const view = page.getByRole("checkbox", { name: "Declined or withdrawn" });
    await view.waitFor({ timeout: 15000 });
    await view.click();
    await page.keyboard.press("Escape");
    await page.getByText("Noor Haddad").first().waitFor({ timeout: 10000 });
    await shot(page, "quick-view-refused");
    pass("CR4.2", "Noor is listed under Declined or withdrawn");
  });

  console.log("\n\x1b[1;36m== CR5  Share with the client (phase 3)\x1b[0m");
  const shareBefore = linkFor(NOOR, PRIVACY_LINK);
  await step("CR5.1", "turning Noor visible asks first; Ask first sends a share request in the confidential wording", async () => {
    await api(`/projects/${position}/candidates/${CANDIDATE}/client-visibility`, {
      method: "PUT", token, body: { clientVisible: false },
    });
    await page.goto(`${WEB}/projects/${position}/companies/universe?view=pipeline`);
    const card = page.getByRole("article", { name: "Noor Haddad" });
    await card.getByText("No consent to share").waitFor({ timeout: 15000 });
    await shot(page, "pipeline-badge-none");
    await card.getByRole("switch", { name: "Visible to client: Noor Haddad" }).click();
    const prompt = page.getByRole("dialog", { name: `No consent to share with ${CLIENT} yet` });
    await prompt.waitFor({ timeout: 10000 });
    await page.waitForTimeout(400);
    await shot(page, "share-prompt");
    await prompt.getByRole("button", { name: "Ask first" }).click();
    const ask = page.getByRole("dialog", { name: "Ask to share Noor Haddad's profile" });
    await ask.waitFor({ timeout: 10000 });
    await page.waitForTimeout(400);
    await shot(page, "share-ask");
    await ask.getByRole("button", { name: "Send request" }).click();
    await card.getByText("Consent asked").waitFor({ timeout: 10000 });
    const row = (await api(`/projects/${position}/share-consents`, { token })).body.rows[0];
    check("CR5.1", "the share is asked, the client not named, Noor still internal", "requested|false",
      `${row?.status}|${row?.clientNamed}`);
  });

  await step("CR5.2", "Noor agrees on their page, and the card says Consent given", async () => {
    const shareLink = await nextLinkFor(NOOR, PRIVACY_LINK, shareBefore);
    if (!shareLink) throw new Error("no share request reached the person");
    await outside.goto(shareLink.replace("http://localhost:5173", WEB));
    const block = outside.getByRole("group", { name: "Share your profile with a client" });
    await block.waitFor({ timeout: 15000 });
    await outside.getByText("We would like to send your profile to a confidential client for a Chief Financial Officer role.").waitFor();
    await shot(outside, "public-share");
    await block.getByRole("button", { name: "Yes" }).click();
    await outside.getByRole("button", { name: /^Send my answer/ }).click();
    await outside.getByRole("heading", { name: "Thank you, your answers are saved" }).waitFor({ timeout: 10000 });
    await page.reload();
    const card = page.getByRole("article", { name: "Noor Haddad" });
    await card.getByText("Consent given").waitFor({ timeout: 15000 });
    await shot(page, "pipeline-badge-given");
    await card.getByRole("switch", { name: "Visible to client: Noor Haddad" }).click();
    await page.waitForTimeout(800);
    check("CR5.2", "with consent given the switch shows them without asking", 0,
      await page.getByRole("dialog", { name: /No consent to share/ }).count());
  });

  await step("CR5.3", "the position's consent register carries the share, and no address", async () => {
    const response = await fetch(`${API}/projects/${position}/export/consent-register`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const csv = await response.text();
    check("CR5.3", "the register lists Noor's given share and no email", "true|true|false",
      `${csv.includes("Noor Haddad")}|${csv.includes("Share with client") && csv.includes("Given")}|${csv.includes(NOOR)}`);
  });
} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);

/** The Candidates page's Filter → Add filter → Show, its view list dropped open. */
async function openShowFilter(page) {
  await page.getByRole("button", { name: /^Filter/ }).first().click();
  await page.getByRole("button", { name: "Add filter" }).click();
  await page.getByRole("group", { name: "Choose a column to filter" }).getByRole("button", { name: "Show" }).click();
}
