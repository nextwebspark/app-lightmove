// The terms tick, re-acceptance and the DPA record (#928) in a real browser against a real API: signup refuses to go
// on until the box is ticked, the founding step asks for the data processing agreement only where the deployment names
// one, an invitee ticks the same box, and changed terms cover the app until they are accepted.
//
// A first provider sign-in's terms step needs a real Google or LinkedIn round trip, so it is left to
// OAuthLoginIntegrationTest and ProviderTermsPage.test.tsx.
//
// Needs `npm run dev` or e2e/stack/up.sh; mail prints to the API console. For the agreement's offered case, boot the
// API with LEGAL_DPA_VERSION and LEGAL_DPA_URL (EXTRA_ENV="LEGAL_DPA_VERSION=… LEGAL_DPA_URL=…" for the stack).
//
//   API_LOG=<the API's console log> PG_CONTAINER=<its postgres container> node e2e/spa/terms-acceptance.mjs

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { fillSignIn } from "./sign-in.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = process.env.API_LOG ?? join(RUN_DIR, "api.log");
const PG_CONTAINER = process.env.PG_CONTAINER ?? "lm-auth-test-pg";
const WEB = process.env.WEB ?? "http://localhost:5173";
const API = process.env.API ?? "http://localhost:8080/api/v1";
const PASSWORD = "Passw0rd123";
const DOMAIN = "nextwebspark.com";
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
const TERMS_REQUIRED = "Tick the box to accept the Terms and Privacy Policy.";
const DPA_REQUIRED = "Tick the box to accept the Data Processing Agreement.";

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

async function awaitLink(address, pattern) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const link = linkFor(address, pattern);
    if (link) return link;
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`no link for ${address}`);
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

/** As the container's superuser, above row-level security: `app_lm_user` is global anyway. */
const sql = (statement) =>
  execFileSync("docker", ["exec", PG_CONTAINER, "psql", "-Atq", "-U", "postgres", "-d", "lightmove", "-c", statement],
    { encoding: "utf8" }).trim();

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const shot = (target, name) => target.screenshot({ path: join(SHOTS, `terms-acceptance-${name}.png`), fullPage: true });
const termsBox = (target) => target.getByRole("checkbox", { name: /I agree to the Terms/ });

const FOUNDER = `lm-e2e-terms-${STAMP}@${DOMAIN}`;
const INVITEE = `lm-e2e-terms-invitee-${STAMP}@${DOMAIN}`;

try {
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();

  console.log("\n\x1b[1;36m== TA1  Signup asks for the tick\x1b[0m");
  await step("TA1.1", "Continue with the box unticked says why and stays on the form", async () => {
    await page.goto(`${WEB}/signup`);
    await page.getByPlaceholder("Yara Haddad").fill("Tara Terms");
    await page.getByPlaceholder("you@firm.com").fill(FOUNDER);
    await page.getByPlaceholder("8+ characters").fill(PASSWORD);
    await page.getByPlaceholder("Re-enter your password").fill(PASSWORD);
    check("TA1.0", "the box starts unticked", false, await termsBox(page).isChecked());
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.getByText(TERMS_REQUIRED).waitFor({ timeout: 5000 });
    await shot(page, "signup-unticked");
    check("TA1.1", "Continue with the box unticked says why and stays on the form", `${WEB}/signup`, page.url());
  });

  await step("TA1.2", "ticked, the account is made and the acceptance recorded", async () => {
    await termsBox(page).check();
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL(/\/signup\/verify-email/, { timeout: 15000 });
    const stored = sql(`SELECT terms_accepted_at IS NOT NULL FROM app_lm_user WHERE email = '${FOUNDER}'`);
    check("TA1.2", "ticked, the account is made and the acceptance recorded", "t", stored);
  });

  console.log("\n\x1b[1;36m== TA2  The founding step and the data processing agreement\x1b[0m");
  const legal = await (async () => {
    const verify = await awaitLink(FOUNDER, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
    await page.goto(verify);
    // The link settles on a success card with a Continue button rather than redirecting by itself.
    await page.getByRole("button", { name: "Continue", exact: true }).click({ timeout: 20000 });
    await page.waitForURL(/\/signup\/workspace/, { timeout: 20000 });
    const session = await api("/auth/login", { method: "POST", body: { email: FOUNDER, password: PASSWORD } });
    return (await api("/onboarding/legal", { token: session.body.accessToken })).body;
  })().catch((error) => { fail("TA2.0", "the founding step opens after the emailed link", String(error)); return null; });

  if (legal) {
    await page.getByRole("radio", { name: /In-house talent team/ }).click();
    await page.getByPlaceholder("e.g. Meridian Search Partners").fill(`Terms Firm ${STAMP}`);
    const agreement = page.getByRole("checkbox", { name: /Data Processing Agreement/ });

    if (legal.dpaVersion) {
      await step("TA2.1", "the agreement the deployment names is asked for, unticked", async () => {
        await agreement.waitFor({ timeout: 5000 });
        await page.getByRole("button", { name: "Continue", exact: true }).click();
        await page.getByText(DPA_REQUIRED).waitFor({ timeout: 5000 });
        await shot(page, "dpa-unticked");
        check("TA2.1", "the agreement the deployment names is asked for, unticked", `${WEB}/signup/workspace`, page.url());
      });
      await step("TA2.2", "ticked, the workspace records which agreement, when and by whom", async () => {
        await agreement.check();
        await page.getByRole("button", { name: "Continue", exact: true }).click();
        await page.waitForURL((url) => !url.pathname.startsWith("/signup/workspace"), { timeout: 15000 });
        const row = sql(`SELECT w.dpa_version || '|' || (w.dpa_accepted_at IS NOT NULL) || '|' || u.email
                         FROM app_lm_workspace w JOIN app_lm_user u ON u.id = w.dpa_accepted_by
                         WHERE w.name = 'Terms Firm ${STAMP}'`);
        check("TA2.2", "ticked, the workspace records which agreement, when and by whom",
          `${legal.dpaVersion}|true|${FOUNDER}`, row);
      });
    } else {
      await step("TA2.1", "a deployment naming no agreement asks for none, and records none", async () => {
        check("TA2.1a", "no agreement box on the founding step", 0, await agreement.count());
        await page.getByRole("button", { name: "Continue", exact: true }).click();
        await page.waitForURL((url) => !url.pathname.startsWith("/signup/workspace"), { timeout: 15000 });
        check("TA2.1", "a deployment naming no agreement asks for none, and records none", "",
          sql(`SELECT coalesce(dpa_version, '') FROM app_lm_workspace WHERE name = 'Terms Firm ${STAMP}'`));
      });
    }
  }

  console.log("\n\x1b[1;36m== TA3  An invitee ticks the same box\x1b[0m");
  await step("TA3.1", "the invited signup will not go on unticked, and lands in the workspace ticked", async () => {
    const admin = (await api("/auth/login", { method: "POST", body: { email: FOUNDER, password: PASSWORD } })).body.accessToken;
    const invited = await api("/invitations", { method: "POST", token: admin, body: [{ email: INVITEE, role: "MEMBER" }] });
    if (invited.status >= 300) throw new Error(`invite answered ${invited.status}`);
    const link = await awaitLink(INVITEE, /http:\/\/localhost:5173\/auth\/accept-invite\?token=[A-Za-z0-9_%.~-]+/);
    const invitee = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
    await invitee.goto(link);
    await invitee.getByPlaceholder("Yara Haddad").fill("Ivo Invitee");
    await invitee.getByPlaceholder("8+ characters").fill(PASSWORD);
    await invitee.getByPlaceholder("Re-enter your password").fill(PASSWORD);
    await invitee.getByRole("button", { name: /Accept & join/ }).click();
    await invitee.getByText(TERMS_REQUIRED).waitFor({ timeout: 5000 });
    check("TA3.0", "no account is made before the tick", "0", sql(`SELECT count(*) FROM app_lm_user WHERE email = '${INVITEE}'`));
    await shot(invitee, "invite-unticked");
    await termsBox(invitee).check();
    await invitee.getByRole("button", { name: /Accept & join/ }).click();
    await invitee.waitForURL((url) => !url.pathname.startsWith("/auth/accept-invite"), { timeout: 15000 });
    check("TA3.1", "the invited signup will not go on unticked, and lands in the workspace ticked", "t",
      sql(`SELECT terms_accepted_at IS NOT NULL FROM app_lm_user WHERE email = '${INVITEE}'`));
  });

  console.log("\n\x1b[1;36m== TA4  Changed terms are asked again\x1b[0m");
  await step("TA4.1", "an older accepted version covers the app with the sheet, and the tick clears it", async () => {
    sql(`UPDATE app_lm_user SET privacy_policy_version = '2020-01-01' WHERE email = '${FOUNDER}'`);
    const signedIn = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
    await signedIn.goto(`${WEB}/login`);
    await fillSignIn(signedIn, FOUNDER, PASSWORD);
    const sheet = signedIn.getByRole("dialog", { name: "We've updated our terms" });
    await sheet.waitFor({ timeout: 20000 });
    await shot(signedIn, "updated-terms");
    await signedIn.keyboard.press("Escape");
    check("TA4.0", "Escape does not dismiss the sheet", true, await sheet.isVisible());
    await sheet.getByRole("button", { name: "Accept and continue" }).click();
    await sheet.getByText(TERMS_REQUIRED).waitFor({ timeout: 5000 });
    await termsBox(sheet).check();
    await sheet.getByRole("button", { name: "Accept and continue" }).click();
    await sheet.waitFor({ state: "detached", timeout: 10000 });
    const version = sql(`SELECT privacy_policy_version FROM app_lm_user WHERE email = '${FOUNDER}'`);
    check("TA4.1", "an older accepted version covers the app with the sheet, and the tick clears it", true,
      version !== "2020-01-01" && version.length > 0);
  });
} finally {
  await browser.close();
  console.log(`\nterms-acceptance: ${passed} passed, ${failed} failed`);
  process.exitCode = failed > 0 ? 1 : 0;
}
