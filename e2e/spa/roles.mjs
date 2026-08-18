// Phase 5 — the SPA under each workspace role, with the UI treated as untrusted.
//
// Two questions, and only the second one is about security:
//
//   1. What does each role actually SEE? Nav, deep links, error states. A member shown an admin
//      button that 403s is a UX bug; a client shown a staff surface is worse.
//   2. For everything the SPA hides, does the SERVER refuse it too? Hiding a control is presentation.
//      The assertion that matters is made from inside that user's own browser session, against the
//      API, with their own credentials.

// Bare specifier: playwright is a devDependency of apps/web, hoisted to the repo root by the npm
// workspace, and Node's resolver walks up from e2e/ to find it. See the note in run.mjs.
import { chromium } from "playwright";
import { readFileSync, mkdirSync, appendFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const WEB = "http://localhost:5173";
const API = "http://localhost:8080/api/v1";
const PASSWORD = "Passw0rd123";

mkdirSync(SHOTS, { recursive: true });

// cast.env is shell; take the assignments we need.
const cast = Object.fromEntries(
  readFileSync(join(RUN_DIR, "cast.env"), "utf8")
    .split("\n")
    .filter((line) => line.includes("="))
    .map((line) => {
      const index = line.indexOf("=");
      // Values are %q-quoted by fixtures.sh, so spaces arrive backslash-escaped.
      return [line.slice(0, index), line.slice(index + 1).replace(/\\(.)/g, "$1")];
    }),
);

let passed = 0;
let failed = 0;
const record = (id, result, detail) =>
  appendFileSync(join(RUN_DIR, "cases.tsv"), `${id}\t${result}\t${detail}\n`);
const pass = (id, what) => { passed++; console.log(`  \x1b[32mPASS\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "PASS", what); };
const fail = (id, what, why) => { failed++; console.log(`  \x1b[31mFAIL\x1b[0m ${id.padEnd(7)} ${what}\n          \x1b[2m${why}\x1b[0m`); record(id, "FAIL", `${what} -- ${why}`); };
const note = (id, what) => { console.log(`  \x1b[2mNOTE\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "NOTE", what); };
const section = (title) => console.log(`\n\x1b[1;36m== ${title}\x1b[0m`);
const check = (id, what, expected, actual) =>
  String(expected) === String(actual) ? pass(id, what) : fail(id, what, `expected [${expected}] got [${actual}]`);

const browser = await chromium.launch();

async function signIn(email, label) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  await page.goto(`${WEB}/login`);
  await page.getByPlaceholder("you@firm.com").fill(email);
  await page.getByPlaceholder("••••••••").fill(PASSWORD);
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.waitForTimeout(3000);
  await page.screenshot({ path: join(SHOTS, `roles-${label}-home.png`), fullPage: true });
  return { context, page };
}

// What the sidebar offers this role, as plain text.
const navOf = (page) =>
  page.evaluate(() =>
    Array.from(document.querySelectorAll("nav a")).map((a) => a.textContent.trim().replace(/\s+/g, " ")),
  );

// Call the API from inside the browser session, with a token this script fetches for that same user.
// The SPA keeps its access token in a module variable, unreachable from an injected script.
const callApi = (page, email, method, path, body) =>
  page.evaluate(async ({ api, email, password, method, path, body }) => {
    const session = await fetch(`${api}/auth/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    }).then((r) => r.json());
    const response = await fetch(`${api}${path}`, {
      method,
      credentials: "include",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${session.accessToken}` },
      body: body ? JSON.stringify(body) : undefined,
    });
    let code = null;
    try { code = (await response.json()).code ?? null; } catch { /* 204 has no body */ }
    return { status: response.status, code };
  }, { api: API, email, password: PASSWORD, method, path, body });

async function landsOn(page, path) {
  await page.goto(`${WEB}${path}`);
  await page.waitForTimeout(2500);
  return new URL(page.url()).pathname;
}

try {
  // ------------------------------------------------------------------ S1
  section("S1  what each role sees");

  const admin = await signIn(cast.ADMIN_EMAIL, "admin");
  const adminNav = await navOf(admin.page);
  note("S1.1", `ADMIN nav: ${adminNav.join(" | ")}`);
  check("S1.2", "the admin is offered Settings", true, adminNav.some((i) => /settings/i.test(i)));

  const member = await signIn(cast.MEMBER_EMAIL, "member");
  const memberNav = await navOf(member.page);
  note("S1.3", `MEMBER nav: ${memberNav.join(" | ")}`);
  // Settings stopped being admin-only when the Account group arrived: Profile is the caller's own
  // account, so the rail offers it to every staff member and lands them on that section. The workspace
  // sections behind it are still admin-gated — S2.1 and S2.2 are what hold that line.
  check("S1.4", "a plain member is offered Settings — their own account", true, memberNav.some((i) => /settings/i.test(i)));
  check("S1.5", "but is offered Clients", true, memberNav.some((i) => /clients/i.test(i)));
  check("S1.6", "and Team", true, memberNav.some((i) => /team/i.test(i)));

  const client = await signIn(cast.CLIENT_EMAIL, "client");
  const clientNav = await navOf(client.page);
  note("S1.7", `PURE CLIENT nav: ${clientNav.join(" | ")}`);
  check("S1.8", "a pure client is offered no Clients registry", false, clientNav.some((i) => /clients/i.test(i)));
  check("S1.9", "no Team", false, clientNav.some((i) => /team/i.test(i)));
  check("S1.10", "and no Settings", false, clientNav.some((i) => /settings/i.test(i)));

  const dual = await signIn(cast.DUAL_EMAIL, "dual");
  const dualNav = await navOf(dual.page);
  note("S1.11", `DUAL (staff + client) nav: ${dualNav.join(" | ")}`);
  check("S1.12", "the dual-role member keeps the staff nav", true, dualNav.some((i) => /clients/i.test(i)));
  check("S1.13", "and is not demoted to the portal view", true, dualNav.some((i) => /team/i.test(i)));

  // ------------------------------------------------------------------ S2
  section("S2  deep links past the nav");

  check("S2.1", "a member deep-linking to /settings/members is bounced", "/", await landsOn(member.page, "/settings/members"));
  check("S2.2", "and to /settings/general", "/", await landsOn(member.page, "/settings/general"));
  await member.page.screenshot({ path: join(SHOTS, "roles-member-settings-deeplink.png"), fullPage: true });

  // The other half of the same rule: the workspace sections bounce them, their own account does not.
  check("S2.2b", "but a member reaches their own profile", "/settings/profile", await landsOn(member.page, "/settings/profile"));
  const memberProfile = await member.page.locator("body").innerText();
  check("S2.2c", "which carries their own identity", true, /Mel Member/.test(memberProfile));
  check("S2.2d", "and offers them no workspace sections", false, /general/i.test((await navOf(member.page)).join(" ")));

  // OPEN BUG (see workspace-role-findings.md, B1). Only /settings/* is guarded, by RequireAdmin.
  // /clients and /team sit under RequireWorkspace alone, so a portal guest who types the URL is
  // served the staff page. The API refuses the data, but the surface renders.
  check("S2.3", "a pure client deep-linking to /clients is bounced", "/", await landsOn(client.page, "/clients"));
  await client.page.screenshot({ path: join(SHOTS, "roles-client-clients-deeplink.png"), fullPage: true });
  const clientsPageText = await client.page.locator("body").innerText();
  check("S2.3b", "and is not offered a New client button", 0,
    await client.page.getByRole("button", { name: /new client/i }).count());
  check("S2.3c", "nor told the firm has zero clients", false, /0 clients/.test(clientsPageText));

  check("S2.4", "a pure client deep-linking to /team is bounced", "/", await landsOn(client.page, "/team"));
  await client.page.screenshot({ path: join(SHOTS, "roles-client-team-deeplink.png"), fullPage: true });
  check("S2.5", "and to /settings/general is bounced", "/", await landsOn(client.page, "/settings/general"));

  // A portal guest has a name and a timezone like anyone else, so Account is theirs too. Their rail
  // carries no Settings (S1.10) by design — the topbar's "Your profile" is their way in, and the route
  // must therefore admit them rather than bounce them like the workspace sections above.
  check("S2.5b", "but a pure client reaches their own profile", "/settings/profile", await landsOn(client.page, "/settings/profile"));
  check("S2.5c", "which carries their own identity", true,
    /Cass Client/.test(await client.page.locator("body").innerText()));

  // The mandate they are not attached to. The list never shows it, but the URL is guessable.
  const unattached = await landsOn(client.page, `/projects/${cast.OTHER_PROJECT_ID}`);
  note("S2.6", `a pure client deep-linking to an unattached mandate lands on ${unattached}`);
  const unattachedText = await client.page.locator("body").innerText();
  check("S2.7", "and is not shown that mandate's brief", false, /position brief|seniority|compensation/i.test(unattachedText));
  await client.page.screenshot({ path: join(SHOTS, "roles-client-unattached-project.png"), fullPage: true });

  const attached = await landsOn(client.page, `/projects/${cast.PROJECT_ID}`);
  check("S2.8", "while their own mandate opens", `/projects/${cast.PROJECT_ID}`, attached);
  await client.page.screenshot({ path: join(SHOTS, "roles-client-attached-project.png"), fullPage: true });

  // ------------------------------------------------------------------ S3
  section("S3  the UI is untrusted — the server refuses what the nav hides");

  const memberDenied = [
    ["S3.1", "GET /invitations", "GET", "/invitations", null, 403],
    ["S3.2", "POST /invitations", "POST", "/invitations", [{ email: "probe@nextwebspark.com", role: "MEMBER" }], 403],
    ["S3.3", "PATCH /workspace", "PATCH", "/workspace", { name: cast.WORKSPACE_NAME }, 403],
    ["S3.4", "DELETE /workspace", "DELETE", "/workspace", { confirmName: cast.WORKSPACE_NAME }, 403],
  ];
  for (const [id, what, method, path, body, want] of memberDenied) {
    const result = await callApi(member.page, cast.MEMBER_EMAIL, method, path, body);
    check(id, `a member calling ${what} directly`, want, result.status);
  }

  const clientDenied = [
    ["S3.5", "GET /members", "GET", "/members", null, 403],
    ["S3.6", "GET /clients", "GET", "/clients", null, 403],
    ["S3.7", "GET /workspace", "GET", "/workspace", null, 403],
    ["S3.8", "GET /companies/sectors", "GET", "/companies/sectors", null, 403],
    ["S3.9", "POST /projects", "POST", "/projects", { clientId: cast.CLIENT_ID, positionTitle: "Client Made" }, 403],
    ["S3.10", "GET the unattached mandate's brief", "GET", `/projects/${cast.OTHER_PROJECT_ID}/position`, null, 403],
  ];
  for (const [id, what, method, path, body, want] of clientDenied) {
    const result = await callApi(client.page, cast.CLIENT_EMAIL, method, path, body);
    check(id, `a pure client calling ${what} directly`, want, result.status);
  }

  // The dual-role member must NOT be caught by the client fence on the same calls.
  for (const [id, what, path] of [
    ["S3.11", "GET /members", "/members"],
    ["S3.12", "GET /clients", "/clients"],
    ["S3.13", "GET /workspace", "/workspace"],
  ]) {
    const result = await callApi(dual.page, cast.DUAL_EMAIL, "GET", path, null);
    check(id, `the dual-role member calling ${what} is allowed`, 200, result.status);
  }

  // ------------------------------------------------------------------ S4
  section("S4  what the client's own screens disclose");

  await client.page.goto(`${WEB}/`);
  await client.page.waitForTimeout(2500);
  const clientHome = await client.page.locator("body").innerText();
  check("S4.1", "the client's home does not list the other client's mandate", false, /Head of Trading/i.test(clientHome));
  check("S4.2", "nor the mandate created after they joined", false, /Dual Created/i.test(clientHome));
  check("S4.3", "their own mandate is there", true, /Chief Financial Officer/i.test(clientHome));
  note("S4.4", `the client's home shows the firm's workspace name: ${/Meridian/i.test(clientHome)}`);

  await client.page.goto(`${WEB}/projects/${cast.PROJECT_ID}/team`);
  await client.page.waitForTimeout(2500);
  const teamTab = await client.page.locator("body").innerText();
  // This one is handled properly and is worth pinning: the tab opens, says view-only, and shows the
  // staff on the mandate plus the client's own contacts. Disclosure by design, not by accident.
  check("S4.5", "the mandate's Team & access tab opens for the client",
    `/projects/${cast.PROJECT_ID}/team`, new URL(client.page.url()).pathname);
  check("S4.6", "and says plainly that it is view-only", true, /view-only access/i.test(teamTab));
  check("S4.7", "it names the staff working their mandate", true, /Ada Admin/.test(teamTab));
  check("S4.8", "and their own contact", true, /Cass Client/.test(teamTab));
  await client.page.screenshot({ path: join(SHOTS, "roles-client-team-tab.png"), fullPage: true });

  const projectNav = await navOf(client.page);
  note("S4.9", `the project sidebar offered to a portal guest: ${projectNav.join(" | ")}`);

  // ------------------------------------------------------------------ S5
  section("S5  an admin's own screens still work end to end");

  check("S5.1", "the admin reaches the members settings page", "/settings/members", await landsOn(admin.page, "/settings/members"));
  const membersPage = await admin.page.locator("body").innerText();
  check("S5.2", "and the roster renders the invited member", true, membersPage.includes("Mel Member"));
  check("S5.3", "while the pure client is absent from it", false, membersPage.includes("Cass Client"));
  await admin.page.screenshot({ path: join(SHOTS, "roles-admin-members.png"), fullPage: true });

  check("S5.4", "the admin reaches general settings", "/settings/general", await landsOn(admin.page, "/settings/general"));
  await admin.page.screenshot({ path: join(SHOTS, "roles-admin-general.png"), fullPage: true });

  // Bare /settings redirects to the landing section, which is Profile for everyone — the only section
  // a non-admin may read, so the redirect cannot depend on the caller's role.
  check("S5.5", "bare /settings lands on Profile", "/settings/profile", await landsOn(admin.page, "/settings"));
  const adminProfile = await admin.page.locator("body").innerText();
  check("S5.6", "and the admin's own profile states the standing it will not let them edit", true,
    /set by workspace owner/.test(adminProfile));
  await admin.page.screenshot({ path: join(SHOTS, "roles-admin-profile.png"), fullPage: true });
} catch (error) {
  fail("SPA", "the browser run completed", String(error).slice(0, 400));
} finally {
  await browser.close();
}

console.log(`\n\x1b[1;36m---- spa/roles.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);

// See run.mjs: an unattended run needs the tally as an exit code, not only on stdout.
process.exitCode = failed > 0 ? 1 : 0;
