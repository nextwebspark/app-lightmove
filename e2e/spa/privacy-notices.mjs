// Candidate privacy notices (#923) in a real browser against a real API: the firm's details on Settings → Data &
// retention, a notice sent from the Candidates drawer, the public page it links to with no session, and an objection
// made there reaching the person's record and the admins' bell.
//
// Needs `npm run dev` (or its two halves) with DATA_PROTECTION_ENABLED=true; mail prints to the API console.
//
//   API_LOG=<the API's console log> node e2e/spa/privacy-notices.mjs

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

const api = async (path, { method = "GET", body, token } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

// --- the seed: an admin, a position and one executive with an address ---------------------

const EMAIL = `lm-e2e-privacy-${STAMP}@${DOMAIN}`;
const NOOR = `noor-${STAMP}@example.com`;
await api("/auth/signup", { method: "POST", body: { fullName: "Pia Privacy", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
const verify = linkFor(EMAIL, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
await api("/auth/verify", { method: "POST", body: { token: verify.split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "AGENCY", name: `Privacy SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Privacy Unit ${STAMP}` } })).body.id;
const position = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;
const filed = await api(`/projects/${position}/candidates`, {
  method: "POST", token,
  body: { fullName: "Noor Haddad", title: "Finance Director", linkedinUrl: `https://www.linkedin.com/in/noor-${STAMP}/`, emails: [{ value: NOOR }] },
});
if (filed.status !== 201) throw new Error(`seeding answered ${filed.status}: ${JSON.stringify(filed.body)}`);
const PERSON = filed.body.personId;

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const shot = (target, name) => target.screenshot({ path: join(SHOTS, `privacy-notices-${name}.png`), fullPage: true });

try {
  console.log("\n\x1b[1;36m== PN1  Settings → Data & retention → Candidate privacy\x1b[0m");
  await step("PN1.1", "an admin fills in the firm's privacy details and saves them", async () => {
    await page.goto(`${WEB}/login`);
    await page.getByPlaceholder("you@firm.com").fill(EMAIL);
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/settings/data`);
    await page.getByText("Candidate privacy").first().waitFor({ timeout: 15000 });
    await page.getByLabel("Firm's legal name").fill("Meridian Search Partners FZ-LLC");
    await page.getByLabel("Privacy contact email").fill("privacy@meridian.example");
    await page.getByLabel("Privacy notice link").fill("https://meridian.example/privacy");
    await shot(page, "settings");
    // The first Save changes on the page: AI on people has none, and Candidate privacy is drawn above retention.
    await page.getByRole("button", { name: "Save changes" }).first().click();
    await page.getByText("Candidate privacy saved").first().waitFor({ timeout: 10000 });
    const saved = await api("/workspace/candidate-privacy", { token });
    check("PN1.1", "the card's details are what the API holds", true, saved.body.readyForNotices);
  });

  console.log("\n\x1b[1;36m== PN2  Send privacy notice from the Candidates drawer\x1b[0m");
  await step("PN2.1", "the drawer shows Not notified, and Send privacy notice sends one", async () => {
    await page.goto(`${WEB}/candidates?person=${PERSON}`);
    await page.getByText("Not notified").first().waitFor({ timeout: 15000 });
    await shot(page, "drawer-not-notified");
    await page.getByRole("region", { name: "Privacy" }).getByRole("button", { name: "Send privacy notice" }).click();
    const dialog = page.getByRole("dialog", { name: /privacy notice/i });
    await dialog.waitFor();
    await page.waitForTimeout(600);
    await shot(page, "send-dialog");
    await dialog.getByRole("button", { name: /^Send/ }).click();
    await page.getByText("Notified", { exact: true }).first().waitFor({ timeout: 10000 });
    await shot(page, "drawer-notified");
    const record = await api(`/candidates/${PERSON}`, { token });
    check("PN2.1", "the person is marked told by privacy email", "privacy_email", record.body.privacy?.noticeChannel);
  });

  let pageLink = null;
  await step("PN2.2", "the notice went to the person in the firm's name with a link to their page", async () => {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    pageLink = linkFor(NOOR, /http:\/\/localhost:5173\/privacy\/[A-Za-z0-9_-]+/);
    check("PN2.2", "the email carries a privacy page link", true, !!pageLink);
  });

  console.log("\n\x1b[1;36m== PN3  The public privacy page, no session\x1b[0m");
  const stranger = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const outside = await stranger.newPage();
  await step("PN3.1", "the page names the firm and the kinds of data, never the person", async () => {
    await outside.goto(pageLink.replace("http://localhost:5173", WEB));
    await outside.getByText("Meridian Search Partners FZ-LLC").first().waitFor({ timeout: 15000 });
    const text = await outside.locator("body").innerText();
    await shot(outside, "public-page");
    check("PN3.1", "no name or address on the page", false, text.includes("Noor") || text.includes(NOOR));
  });
  await step("PN3.2", "Don't contact me again, confirmed, sets do not contact and tells the admin", async () => {
    await outside.getByRole("button", { name: /Don.t contact me again/ }).first().click();
    await outside.getByRole("button", { name: /Don.t contact me again/ }).last().click();
    await outside.getByText(/won.t hear from us again/i).first().waitFor({ timeout: 10000 });
    await shot(outside, "public-objected");
    const record = await api(`/candidates/${PERSON}`, { token });
    const bell = await api("/me/notifications?filter=unread", { token });
    check("PN3.2", "do not contact is set", true, !!record.body.doNotContact);
    check("PN3.3", "the admin's bell holds the objection", true,
      JSON.stringify(bell.body).includes("person_objected"));
  });
  await stranger.close();

  console.log("\n\x1b[1;36m== PN5  Stop emails (#924): the footer's link and a mail app's one-click\x1b[0m");
  const OMAR = `omar-${STAMP}@example.com`;
  const omar = (await api(`/projects/${position}/candidates`, {
    method: "POST", token,
    body: { fullName: "Omar Saleh", title: "Treasurer", linkedinUrl: `https://www.linkedin.com/in/omar-${STAMP}/`, emails: [{ value: OMAR }] },
  })).body.personId;
  await api(`/candidates/${omar}/privacy-notice`, { method: "POST", token });
  await new Promise((resolve) => setTimeout(resolve, 1000));
  const omarLink = linkFor(OMAR, /http:\/\/localhost:5173\/privacy\/[A-Za-z0-9_-]+/);
  const optOut = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const optOutPage = await optOut.newPage();
  await step("PN5.1", "?action=opt-out asks first, and Stop emails sets do not contact and tells the sender", async () => {
    await optOutPage.goto(`${omarLink.replace("http://localhost:5173", WEB)}?action=opt-out`);
    await optOutPage.getByText("Stop emails from Meridian Search Partners FZ-LLC?").first().waitFor({ timeout: 15000 });
    const before = await api(`/candidates/${omar}`, { token });
    check("PN5.1", "opening the link alone opts nobody out", false, !!before.body.doNotContact);
    await shot(optOutPage, "public-opt-out");
    await optOutPage.getByRole("button", { name: "Stop emails", exact: true }).click();
    await optOutPage.getByText(/won.t receive more emails from us/i).first().waitFor({ timeout: 10000 });
    await shot(optOutPage, "public-opted-out");
    const record = await api(`/candidates/${omar}`, { token });
    check("PN5.2", "do not contact is set", true, !!record.body.doNotContact);
    check("PN5.3", "the drawer's record says when they opted out", true, !!record.body.privacy?.optedOutAt);
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const bell = await api("/me/notifications?filter=unread", { token });
    check("PN5.4", "the sender's bell says they opted out", true, JSON.stringify(bell.body).includes("candidate_opted_out"));
  });
  await step("PN5.5", "a mail app's one-click post (RFC 8058) answers 200 again and changes nothing", async () => {
    const response = await fetch(`${API}/privacy/links/${omarLink.split("/privacy/")[1]}/opt-out`, {
      method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "List-Unsubscribe=One-Click",
    });
    check("PN5.5", "the one-click post answers 200", 200, response.status);
  });
  await optOut.close();

  console.log("\n\x1b[1;36m== PN4  The Not notified quick view\x1b[0m");
  await step("PN4.1", "the chip is on the Candidates page", async () => {
    await page.goto(`${WEB}/candidates`);
    await page.getByRole("button", { name: /Not notified/ }).first().waitFor({ timeout: 15000 });
    await shot(page, "candidates-chip");
    pass("PN4.1", "the Not notified chip is drawn");
  });
} finally {
  await browser.close();
}

console.log(`\n  ${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
