// Do not contact as the person's objection (B16, #1489) in a real browser against a real API: the drawer's pill asks
// first, then stops every run and suppresses them as their stop link would; clearing it is refused while the
// suppression list holds them; the consent register carries the objection.
//
// Needs `DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev` (both off by default); mail prints
// to the API console.
//
//   API_LOG=<the API's console log> node e2e/spa/do-not-contact.mjs

import { chromium } from "playwright";
import { appendFileSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { fillSignIn } from "./sign-in.mjs";

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
  let parsed = text;
  try { parsed = text ? JSON.parse(text) : null; } catch { /* a CSV */ }
  return { status: response.status, body: parsed };
};

// --- the seed: an admin, a position and one executive with an address ---------------------

const EMAIL = `lm-e2e-dnc-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Dana Contact", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
const verify = linkFor(EMAIL, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
await api("/auth/verify", { method: "POST", body: { token: verify.split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "AGENCY", name: `DNC SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `DNC Unit ${STAMP}` } })).body.id;
const position = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;
const PERSON = (await api(`/projects/${position}/candidates`, {
  method: "POST", token,
  body: { fullName: "Karim Nassar", linkedinUrl: `https://www.linkedin.com/in/karim-${STAMP}/`, emails: [{ value: `karim-${STAMP}@example.com` }] },
})).body.personId;

console.log("\nDo not contact as an objection (#1489)");
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
try {
  await step("DNC.1", "the pill asks first, then records the objection", async () => {
    await page.goto(`${WEB}/login`);
    await fillSignIn(page, EMAIL, PASSWORD);
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/candidates?person=${PERSON}`);
    await page.getByRole("heading", { name: "Karim Nassar" }).waitFor({ timeout: 15000 });
    await page.waitForTimeout(800);
    await page.getByRole("button", { name: "Do not contact" }).click();
    const dialog = page.getByRole("dialog", { name: "Mark Karim Nassar do not contact?" });
    await dialog.waitFor({ timeout: 5000 });
    await page.waitForTimeout(600);
    await page.screenshot({ path: join(SHOTS, "do-not-contact-dialog.png") });
    await dialog.getByRole("button", { name: "Mark do not contact" }).click();
    await page.getByText("Objected when contacted, recorded by the team").first().waitFor({ timeout: 10000 });
    await page.screenshot({ path: join(SHOTS, "do-not-contact-recorded.png") });
    const person = (await api(`/candidates/${PERSON}`, { token })).body;
    check("DNC.1", "the person is marked as objecting", "Objected when contacted, recorded by the team",
      person.doNotContact?.reason);
  });

  await step("DNC.2", "the suppression list holds them as objected", async () => {
    const entries = (await api("/workspace/suppressions", { token })).body;
    check("DNC.2", "two objected entries (profile and email)", "objected,objected",
      entries.map((entry) => entry.reason).join(","));
  });

  await step("DNC.3", "clearing it is refused while they are suppressed", async () => {
    await page.getByRole("button", { name: "Do not contact" }).click();
    await page.getByText("They objected to being contacted").first().waitFor({ timeout: 10000 });
    const refused = await api(`/candidates/${PERSON}/do-not-contact`, { method: "PUT", token, body: { doNotContact: false } });
    check("DNC.3", "the API answers PERSON_OBJECTION_HELD", "409 PERSON_OBJECTION_HELD",
      `${refused.status} ${refused.body?.code}`);
  });

  await step("DNC.4", "the consent register carries the objection", async () => {
    const csv = (await api("/candidates/export/consent-register", { token })).body;
    const line = String(csv).split("\n").find((row) => row.includes(PERSON)) ?? "";
    check("DNC.4", "an Objection to contact line, recorded by the team",
      true, line.includes("Objection to contact") && line.includes("Recorded by the team"));
  });
} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
