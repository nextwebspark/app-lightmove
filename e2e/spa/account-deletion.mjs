// Account deletion and Download my data (#926) in a real browser against a real API: the only admin of a workspace
// is shown what to hand over, a member downloads their account as JSON and then deletes it from Settings → Profile,
// landing signed out with every way back in gone and the row left as a former member's.
//
//   npm run dev
//   API_LOG=<the API's console log> node e2e/spa/account-deletion.mjs
//
// Assertions read the database as the container's superuser, above row-level security.

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

function verifyTokenFor(address) {
  let mine = false;
  let found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(/\/auth\/verify\?token=([A-Za-z0-9_%.~-]+)/);
      if (match) found = match[1];
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

const verifiedAccount = async (fullName, email) => {
  await api("/auth/signup", { method: "POST", body: { fullName, email, password: PASSWORD, termsAccepted: true } });
  await new Promise((resolve) => setTimeout(resolve, 1500));
  await api("/auth/verify", { method: "POST", body: { token: verifyTokenFor(email) } });
  return (await api("/auth/login", { method: "POST", body: { email, password: PASSWORD } })).body.accessToken;
};

// --- the seed -------------------------------------------------------------------------

const ADMIN = `lm-e2e-account-admin-${STAMP}@${DOMAIN}`;
const MEMBER = `lm-e2e-account-member-${STAMP}@${DOMAIN}`;
const NAME = `Account Firm ${STAMP}`;
let adminToken = await verifiedAccount("Sara Admin", ADMIN);
await api("/onboarding/workspace", { method: "POST", token: adminToken, body: { mode: "COMPANY", name: NAME } });
adminToken = (await api("/auth/login", { method: "POST", body: { email: ADMIN, password: PASSWORD } })).body.accessToken;
const WORKSPACE = sql(`SELECT id FROM app_lm_workspace WHERE name = '${NAME}'`);
let memberToken = await verifiedAccount("Omar Member", MEMBER);
await api("/invitations", { method: "POST", token: adminToken, body: [{ email: MEMBER, role: "MEMBER" }] });
const invitation = sql(`SELECT id FROM app_lm_invitation WHERE workspace_id = '${WORKSPACE}' AND email = '${MEMBER}'`);
await api(`/onboarding/invitations/${invitation}/accept`, { method: "POST", token: memberToken });
memberToken = (await api("/auth/login", { method: "POST", body: { email: MEMBER, password: PASSWORD } })).body.accessToken;
const key = await api("/workspace/api-keys", {
  method: "POST", token: memberToken, body: { name: `BI ${STAMP}`, kind: "PERSONAL", scopes: ["projects:read"] },
});
const MEMBER_ID = sql(`SELECT id FROM app_lm_user WHERE email = '${MEMBER}'`);
console.log(`  seeded workspace ${WORKSPACE}: an admin, a member ${MEMBER_ID} with an API key (${key.status})`);

// --- the browser ----------------------------------------------------------------------

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});

const signedInPage = async (email) => {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  const page = await context.newPage();
  await page.goto(`${WEB}/login`);
  await page.getByPlaceholder("you@firm.com").fill(email);
  await page.getByPlaceholder("••••••••").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
  return page;
};

try {
  await step("AD1.1", "the only admin is shown the workspace to hand over, and nothing is deleted", async () => {
    const page = await signedInPage(ADMIN);
    await page.goto(`${WEB}/settings/profile`);
    await page.getByRole("region", { name: "Delete my account" }).getByRole("button", { name: "Delete…" }).click();
    const dialog = page.getByRole("dialog", { name: "Hand over first" });
    await dialog.waitFor({ timeout: 10000 });
    await page.waitForTimeout(400);
    await page.screenshot({ path: join(SHOTS, "account-handover.png"), fullPage: true });
    check("AD1.1", "the dialog names the workspace", true, (await dialog.textContent()).includes(NAME));
    check("AD1.2", "it offers no way to delete", 0, await dialog.getByRole("button", { name: "Delete my account" }).count());
    check("AD1.3", "the admin is still active", "ACTIVE", sql(`SELECT status FROM app_lm_user WHERE email = '${ADMIN}'`));
    await page.context().close();
  });

  const page = await signedInPage(MEMBER);

  await step("AD2.1", "a member downloads their account as JSON", async () => {
    await page.goto(`${WEB}/settings/profile`);
    const card = page.getByRole("region", { name: "Download my data" });
    await page.screenshot({ path: join(SHOTS, "account-profile-cards.png"), fullPage: true });
    const [download] = await Promise.all([page.waitForEvent("download"), card.getByRole("button", { name: "Download" }).click()]);
    check("AD2.1", "the file is named for the account", true, download.suggestedFilename().startsWith("uncava-account-"));
    const file = JSON.parse(readFileSync(await download.path(), "utf8"));
    check("AD2.2", "it carries the account's address", MEMBER, file.account?.email);
    check("AD2.3", "it carries the workspace by name", NAME, file.workspaces?.[0]?.workspaceName);
    check("AD2.4", "it carries the personal API key, never its secret", `BI ${STAMP}`, file.workspaces?.[0]?.apiKeys?.[0]?.name);
    check("AD2.5", "it carries a sign-in", true, (file.signIns ?? []).some((line) => line.event === "LOGIN_SUCCEEDED"));
    check("AD2.6", "the download is audited", "1",
      sql(`SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'ACCOUNT_DATA_EXPORTED' AND actor_user_id = '${MEMBER_ID}'`));
  });

  await step("AD3.1", "the member deletes their account and lands signed out", async () => {
    await page.getByRole("region", { name: "Delete my account" }).getByRole("button", { name: "Delete…" }).click();
    const dialog = page.getByRole("dialog", { name: "Delete your account?" });
    await dialog.waitFor({ timeout: 10000 });
    await dialog.getByText(`You leave ${NAME} now`, { exact: false }).waitFor({ timeout: 10000 });
    pass("AD3.1", "the dialog names the workspace left");
    const confirm = dialog.getByRole("button", { name: "Delete my account" });
    check("AD3.2", "it waits for the password", true, await confirm.isDisabled());
    await dialog.getByPlaceholder("Enter your password to confirm").fill("notmypassword1");
    await confirm.click();
    await dialog.getByText("That is not your current password").waitFor({ timeout: 10000 });
    pass("AD3.3", "a wrong password is refused under its field");
    await dialog.getByPlaceholder("Enter your password to confirm").fill(PASSWORD);
    await page.waitForTimeout(400);
    await page.screenshot({ path: join(SHOTS, "account-delete-dialog.png"), fullPage: true });
    await confirm.click();
    await page.waitForURL((url) => url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.getByText("Your account was deleted.").waitFor({ timeout: 10000 });
    await page.screenshot({ path: join(SHOTS, "account-deleted-login.png"), fullPage: true });
    pass("AD3.4", "the sign-in page says the account was deleted");

    check("AD3.5", "the row is a former member's", `DELETED|Former member|deleted-${MEMBER_ID}@deleted.invalid|true`,
      sql(`SELECT status || '|' || full_name || '|' || email || '|' || (deleted_at IS NOT NULL) FROM app_lm_user WHERE id = '${MEMBER_ID}'`));
    check("AD3.6", "the membership ended, said as a deletion", "REMOVED|ACCOUNT_DELETED", sql(`SELECT m.status || '|' ||
      (SELECT metadata ->> 'reason' FROM app_lm_audit_event WHERE event_type = 'MEMBER_REMOVED' AND actor_user_id = '${MEMBER_ID}')
      FROM app_lm_workspace_member m WHERE m.user_id = '${MEMBER_ID}'`));
    check("AD3.7", "no session, sign-in or key is left", "0|0|REVOKED", sql(`SELECT
      (SELECT count(*) FROM app_lm_refresh_token WHERE user_id = '${MEMBER_ID}') || '|' ||
      (SELECT count(*) FROM app_lm_user_identity WHERE user_id = '${MEMBER_ID}') || '|' ||
      (SELECT CASE WHEN bool_and(revoked_at IS NOT NULL) THEN 'REVOKED' ELSE 'LIVE' END FROM app_lm_api_key WHERE owner_user_id = '${MEMBER_ID}')`));
    check("AD3.8", "the old address no longer signs in", 401,
      (await api("/auth/login", { method: "POST", body: { email: MEMBER, password: PASSWORD } })).status);
    check("AD3.9", "the old address was told", true, readFileSync(API_LOG, "utf8").includes("Your Uncava account was deleted"));
  });
} finally {
  await browser.close();
}

console.log(`\n  ${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
