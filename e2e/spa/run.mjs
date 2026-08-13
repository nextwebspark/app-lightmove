// Phase 3 — drives the real SPA in headless Chromium.
//
// The curl matrix proves the HTTP contract. This proves the things only a browser can: that the
// three-step wizard routes off server state rather than a step counter, that the single-use
// verification token survives React StrictMode's double-invoke, that a cold reload rebuilds the
// session from the httpOnly refresh cookie alone, and that the route guards actually guard.
//
// Playwright is imported as a bare specifier. There is still no playwright config and no test runner —
// it is a devDependency of apps/web, hoisted to the repo root by the npm workspace, and Node's
// resolver walks up from e2e/ to find it. (It used to be an absolute /Users/... path, which meant the
// browser phase could only ever run on one laptop.)

import { chromium } from "playwright";
import { readFileSync, mkdirSync, appendFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const API_LOG = join(RUN_DIR, "api.log");
const WEB = "http://localhost:5173";
const API = "http://localhost:8080/api/v1";
const PASSWORD = "Passw0rd123";
const DOMAIN = "nextwebspark.com";

mkdirSync(SHOTS, { recursive: true });

let passed = 0;
let failed = 0;
const record = (id, result, detail) =>
  appendFileSync(join(RUN_DIR, "cases.tsv"), `${id}\t${result}\t${detail}\n`);

const pass = (id, what) => { passed++; console.log(`  \x1b[32mPASS\x1b[0m ${id.padEnd(6)} ${what}`); record(id, "PASS", what); };
const fail = (id, what, why) => { failed++; console.log(`  \x1b[31mFAIL\x1b[0m ${id.padEnd(6)} ${what}\n         \x1b[2m${why}\x1b[0m`); record(id, "FAIL", `${what} -- ${why}`); };
const note = (id, what) => { console.log(`  \x1b[2mNOTE\x1b[0m ${id.padEnd(6)} ${what}`); record(id, "NOTE", what); };
const section = (title) => console.log(`\n\x1b[1;36m== ${title}\x1b[0m`);
const check = (id, what, expected, actual) =>
  expected === actual ? pass(id, what) : fail(id, what, `expected [${expected}] got [${actual}]`);

const email = (tag) => `lm-e2e-spa-${tag}-${Date.now()}${Math.floor(Math.random() * 1000)}@${DOMAIN}`;

// The verification link belongs to a specific recipient: LogEmailSender prints `To:` before the body,
// so the last link that follows this address's header line is the one we want.
function linkFor(address, kind = "verify") {
  const lines = readFileSync(API_LOG, "utf8").split("\n");
  let mine = false;
  let found = null;
  for (const line of lines) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(new RegExp(`http://localhost:5173/auth/${kind}\\?token=[A-Za-z0-9_%.~-]+`));
      if (match) found = match[0];
    }
  }
  return found;
}

const shot = (page, name) => page.screenshot({ path: join(SHOTS, `${name}.png`), fullPage: true });

// A false theft detection is the failure mode the cross-tab lock exists to prevent, and it is
// visible in the API log rather than in the browser.
const countReuse = () =>
  (readFileSync(API_LOG, "utf8").match(/Refresh token reuse detected/g) ?? []).length;

async function signupThroughUi(page, address, name = "Spa Tester") {
  await page.goto(`${WEB}/signup`);
  await page.getByPlaceholder("Yara Haddad").fill(name);
  await page.getByPlaceholder("you@firm.com").fill(address);
  await page.getByPlaceholder("8+ characters").fill(PASSWORD);
  await page.getByPlaceholder("Re-enter your password").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue" }).click();
}

const browser = await chromium.launch();

try {
  // ---------------------------------------------------------------- S1
  section("S1  the three-step signup wizard");
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await ctx.newPage();
  const ADMIN = email("admin");

  await signupThroughUi(page, ADMIN, "Ada Admin");
  await page.waitForURL(/\/signup\/workspace/, { timeout: 15000 }).catch(() => {});
  check("S1.1", "step 1 lands on the organization step", `${WEB}/signup/workspace`, page.url());
  await shot(page, "s1-workspace-step");

  const WS = `Spa Search ${Date.now()}`;
  await page.getByPlaceholder("e.g. LightMove Search Partners").fill(WS);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.waitForURL(/\/signup\/invite/, { timeout: 15000 }).catch(() => {});
  check("S1.2", "step 2 lands on the invite step", `${WEB}/signup/invite`, page.url());

  await page.getByPlaceholder("colleague@firm.com").first().fill(email("colleague"));
  await page.getByRole("button", { name: /Send invites|Continue|Finish/i }).first().click();
  await page.waitForURL(/\/signup\/verify/, { timeout: 15000 }).catch(() => {});
  check("S1.3", "an unverified creator is parked on the check-inbox screen", `${WEB}/signup/verify`, page.url());
  await shot(page, "s1-check-inbox");

  const body = await page.locator("body").innerText();
  check("S1.4", "the screen names the address the link went to", true, body.includes(ADMIN));

  // ---------------------------------------------------------------- S2
  section("S2  the emailed link, clicked in the browser");
  const verifyUrl = linkFor(ADMIN, "verify");
  if (!verifyUrl) {
    fail("S2.1", "the verification link was printed", "no link found in the API log for this address");
  } else {
    note("S2.0", `link: ${verifyUrl.slice(0, 72)}...`);
    await page.goto(verifyUrl);
    await page.waitForTimeout(4000);
    const verifyBody = await page.locator("body").innerText();
    await shot(page, "s2-verified");

    // The page settles on a success card with a Continue button rather than redirecting by itself.
    check("S2.1", "the link reports the email as verified", true, /email verified/i.test(verifyBody));
    check("S2.2", "and not as a failure", false, /verification failed/i.test(verifyBody));

    // StrictMode double-invokes effects in dev. Without the useRef guard the token would be burned
    // twice and the second call would report "this link is not valid" over a success that had just
    // happened — so a clean success card IS the proof the guard holds.
    check("S2.3", "no 'link is not valid' from a double-invoked effect", false, /not valid/i.test(verifyBody));

    await page.getByRole("button", { name: "Continue" }).click();
    await page.waitForTimeout(2500);
    check("S2.4", "Continue lands on the workspace home", `${WEB}/`, page.url());
    await shot(page, "s2-verified-home");
  }

  // ---------------------------------------------------------------- S3
  section("S3  session survives a cold reload");
  await page.goto(`${WEB}/`);
  await page.reload();
  await page.waitForTimeout(2500);
  check("S3.1", "a hard reload keeps the user signed in", false, page.url().includes("/login"));
  note("S3.2", `after reload the SPA is at ${page.url()}`);

  // The access token lives in JS memory only — a reload must rebuild it from the refresh cookie.
  const stored = await page.evaluate(() => JSON.stringify({
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));
  check("S3.3", "no token is parked in localStorage or sessionStorage", true,
    !/token|jwt|access/i.test(stored));
  note("S3.4", `web storage keys: ${stored}`);

  const cookies = await ctx.cookies();
  const refresh = cookies.find((c) => c.name === "lm_refresh");
  check("S3.5", "the refresh cookie is httpOnly", true, refresh?.httpOnly === true);
  check("S3.6", "the refresh cookie is path-scoped to the auth routes", "/api/v1/auth", refresh?.path);
  note("S3.7", `refresh cookie sameSite=${refresh?.sameSite} secure=${refresh?.secure} (local dev overrides these)`);

  // ---------------------------------------------------------------- S4
  section("S4  route guards");
  await page.goto(`${WEB}/login`);
  await page.waitForTimeout(1500);
  check("S4.1", "a signed-in user cannot reach /login", false, page.url().endsWith("/login"));

  await page.goto(`${WEB}/signup`);
  await page.waitForTimeout(1500);
  check("S4.2", "nor /signup", false, page.url().endsWith("/signup"));

  const anon = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const anonPage = await anon.newPage();
  await anonPage.goto(`${WEB}/`);
  await anonPage.waitForTimeout(2000);
  check("S4.3", "an anonymous visitor is sent to the login screen", true, anonPage.url().includes("/login"));

  await anonPage.goto(`${WEB}/settings/members`);
  await anonPage.waitForTimeout(2000);
  check("S4.4", "a deep link into settings is guarded too", true, anonPage.url().includes("/login"));
  await shot(anonPage, "s4-login-redirect");

  // ---------------------------------------------------------------- S5
  section("S5  error rendering");
  await anonPage.goto(`${WEB}/login`);
  await anonPage.getByPlaceholder("you@firm.com").fill(ADMIN);
  await anonPage.getByPlaceholder("••••••••").fill("WrongPassword9");
  await anonPage.getByRole("button", { name: "Continue" }).click();
  await anonPage.waitForTimeout(2500);
  const loginBody = await anonPage.locator("body").innerText();
  check("S5.1", "a wrong password shows the vague server message", true,
    /invalid email or password/i.test(loginBody));
  check("S5.2", "and does not leak that the account exists", false,
    /(no account|not found|unknown user|does not exist)/i.test(loginBody));
  await shot(anonPage, "s5-login-error");

  // A duplicate address must offer the log-in route rather than a dead end.
  const dupPage = await (await browser.newContext()).newPage();
  await signupThroughUi(dupPage, ADMIN, "Ada Again");
  await dupPage.waitForTimeout(2500);
  const dupBody = await dupPage.locator("body").innerText();
  check("S5.3", "signing up with a taken address explains itself", true,
    /already exists|already registered/i.test(dupBody));
  check("S5.4", "and offers a route to log in instead", true, /log in/i.test(dupBody));
  await shot(dupPage, "s5-duplicate-email");

  // Client-side password confirmation has no server counterpart — the DTO has no confirmPassword.
  const mismatchPage = await (await browser.newContext()).newPage();
  await mismatchPage.goto(`${WEB}/signup`);
  await mismatchPage.getByPlaceholder("Yara Haddad").fill("Mis Match");
  await mismatchPage.getByPlaceholder("you@firm.com").fill(email("mismatch"));
  await mismatchPage.getByPlaceholder("8+ characters").fill(PASSWORD);
  await mismatchPage.getByPlaceholder("Re-enter your password").fill("SomethingElse1");
  await mismatchPage.getByRole("button", { name: "Continue" }).click();
  await mismatchPage.waitForTimeout(1500);
  check("S5.5", "a password mismatch is caught before the request leaves the browser",
    `${WEB}/signup`, mismatchPage.url());

  const weakPage = await (await browser.newContext()).newPage();
  await weakPage.goto(`${WEB}/signup`);
  await weakPage.getByPlaceholder("Yara Haddad").fill("Weak Pass");
  await weakPage.getByPlaceholder("you@firm.com").fill(email("weak"));
  await weakPage.getByPlaceholder("8+ characters").fill("short");
  await weakPage.getByPlaceholder("Re-enter your password").fill("short");
  await weakPage.getByRole("button", { name: "Continue" }).click();
  await weakPage.waitForTimeout(1500);
  check("S5.6", "a weak password is caught client-side too", `${WEB}/signup`, weakPage.url());
  await shot(weakPage, "s5-weak-password");

  // ---------------------------------------------------------------- S6
  section("S6  the invited colleague's own route");
  const INVITED = email("invited");
  const adminApi = await (await browser.newContext()).newPage();
  await adminApi.goto(`${WEB}/login`);
  await adminApi.getByPlaceholder("you@firm.com").fill(ADMIN);
  await adminApi.getByPlaceholder("••••••••").fill(PASSWORD);
  await adminApi.getByRole("button", { name: "Continue" }).click();
  await adminApi.waitForTimeout(2500);

  // The SPA keeps its access token in a module-level variable, unreachable from an injected script,
  // so the invitation is sent with a token this script obtains for itself.
  const invited = await adminApi.evaluate(async ({ api, address, password, admin }) => {
    const session = await fetch(`${api}/auth/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: admin, password }),
    }).then((r) => r.json());
    const response = await fetch(`${api}/invitations`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${session.accessToken}` },
      body: JSON.stringify([{ email: address, role: "MEMBER" }]),
    });
    return response.status;
  }, { api: API, address: INVITED, password: PASSWORD, admin: ADMIN }).catch((e) => String(e).slice(0, 80));
  check("S6.1", "an admin can invite a colleague", 200, invited);

  const inviteUrl = linkFor(INVITED, "accept-invite");
  if (inviteUrl) {
    const invitePage = await (await browser.newContext()).newPage();
    await invitePage.goto(inviteUrl);
    await invitePage.waitForTimeout(2500);
    const inviteBody = await invitePage.locator("body").innerText();
    check("S6.2", "the invitation screen names the workspace", true, inviteBody.includes(WS));

    // The address sits in a readonly input, so it is a value rather than page text.
    const shownEmail = await invitePage.locator('input[type="email"]').first().inputValue();
    check("S6.3", "the invited address is shown and not editable", INVITED, shownEmail);
    check("S6.4", "the address field is readonly, so the token cannot be pointed elsewhere", true,
      await invitePage.locator('input[type="email"]').first().evaluate((el) => el.readOnly));
    await shot(invitePage, "s6-accept-invite");
  } else {
    note("S6.2", "no invitation email was produced for this address — invite sent via the API instead");
  }

  // ---------------------------------------------------------------- S7
  section("S7  logout");
  await page.goto(`${WEB}/`);
  await page.waitForTimeout(2000);
  // Sign out lives inside the Topbar workspace menu, so the menu has to be opened first.
  await page.locator('button[aria-expanded]').first().click();
  await page.waitForTimeout(600);
  await shot(page, "s7-workspace-menu");

  const signOut = page.getByRole("button", { name: /sign out/i }).first();
  check("S7.1", "the workspace menu offers Sign out", 1, await signOut.count());

  await signOut.click();
  await page.waitForTimeout(3000);
  check("S7.2", "signing out returns to the login screen", true, page.url().includes("/login"));

  const afterLogout = await ctx.cookies();
  check("S7.3", "the refresh cookie is gone from the browser", undefined,
    afterLogout.find((c) => c.name === "lm_refresh")?.value);

  await page.goBack();
  await page.waitForTimeout(3000);
  check("S7.4", "the back button does not restore the signed-in view", true, page.url().includes("/login"));
  await shot(page, "s7-after-logout");

  // ---------------------------------------------------------------- S8
  section("S8  two tabs refreshing at once");
  // A rotated refresh token presented twice reads as theft and revokes the family. The SPA guards
  // this with a cross-tab navigator.locks lock, so two tabs reloading together must not sign the
  // user out.
  const raceCtx = await browser.newContext();
  const tabA = await raceCtx.newPage();
  await tabA.goto(`${WEB}/login`);
  await tabA.getByPlaceholder("you@firm.com").fill(ADMIN);
  await tabA.getByPlaceholder("••••••••").fill(PASSWORD);
  await tabA.getByRole("button", { name: "Continue" }).click();
  await tabA.waitForTimeout(2500);

  const tabB = await raceCtx.newPage();
  const reuseBefore = countReuse();

  // Record what each tab's boot sequence actually got back, so a failure names its own cause.
  const traffic = [];
  const trace = (tab) => (response) => {
    const url = response.url();
    if (url.includes("/auth/refresh") || url.includes("/auth/me") || url.includes("/auth/csrf")) {
      traffic.push(`${tab} ${response.request().method()} ${url.split("/api/v1")[1]} -> ${response.status()}`);
    }
  };
  tabA.on("response", trace("A"));
  tabB.on("response", trace("B"));
  await Promise.all([
    tabA.goto(`${WEB}/`).catch(() => {}),
    tabB.goto(`${WEB}/`).catch(() => {}),
  ]);
  // Each tab does its own refresh-then-me on boot; give both time to settle before judging.
  await Promise.all([
    tabA.waitForLoadState("networkidle").catch(() => {}),
    tabB.waitForLoadState("networkidle").catch(() => {}),
  ]);
  await tabA.waitForTimeout(4000);
  check("S8.1", "tab A survived the simultaneous refresh", false, tabA.url().includes("/login"));
  check("S8.2", "tab B survived it too", false, tabB.url().includes("/login"));
  check("S8.3", "and neither tab was mistaken for a token thief", reuseBefore, countReuse());
  note("S8.4", `boot traffic: ${traffic.join(" | ") || "(none captured)"}`);
  await shot(tabB, "s8-two-tabs");
} catch (error) {
  fail("SPA", "the browser run completed", String(error).slice(0, 400));
} finally {
  await browser.close();
}

console.log(`\n\x1b[1;36m---- spa/run.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);

// The tally has to reach the shell, or an unattended run (run-all.sh, the nightly workflow) reports
// success while printing failures nobody is watching.
process.exitCode = failed > 0 ? 1 : 0;
