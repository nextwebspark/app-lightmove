// #929: the SPA's Content-Security-Policy against the real bundle, in a real browser.
//
// SpaSecurityTest proves the header's text. This proves the bundle lives inside it: every screen that loads a third
// party or injects a style (the Mapbox globes, the rich-text editor, the signature preview, the booking page, the OAuth
// consent screen) opens with no CSP violation, and no page reaches a font host or an error reporter.
//
// The policy is sent by Spring, never by Vite, so WEB must be the API's own origin serving a built bundle:
//   cd apps/web && npm run build && cp -r dist/. ../api/target/classes/static/   (then boot the API)
// Run from e2e/ (`node spa/third-party-loads.mjs`).

import { chromium } from "playwright";
import { readFileSync, mkdirSync, appendFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { fillSignIn } from "./sign-in.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const API_LOG = process.env.API_LOG ?? join(RUN_DIR, "api.log");
const WEB = process.env.WEB ?? "http://localhost:8080";
const API = `${WEB}/api/v1`;
const PASSWORD = "Passw0rd123";
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
const FORBIDDEN_HOSTS = /fonts\.googleapis\.com|fonts\.gstatic\.com|sentry\.io/;

mkdirSync(RUN_DIR, { recursive: true });

let passed = 0;
let failed = 0;
const record = (id, result, detail) => appendFileSync(join(RUN_DIR, "cases.tsv"), `${id}\t${result}\t${detail}\n`);
const pass = (id, what) => { passed++; console.log(`  \x1b[32mPASS\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "PASS", what); };
const fail = (id, what, why) => { failed++; console.log(`  \x1b[31mFAIL\x1b[0m ${id.padEnd(7)} ${what}\n         \x1b[2m${why}\x1b[0m`); record(id, "FAIL", `${what} -- ${why}`); };

function tokenFor(address, kind) {
  let mine = false, found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(new RegExp(`/auth/${kind}\\?token=([A-Za-z0-9_%.~-]+)`));
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

// --- the seed -------------------------------------------------------------------

// A refused step would otherwise surface lines later as a TypeError on its null body.
const seeded = (step, response) => {
  if (response.status >= 400) {
    fail("TP0", `the seed: ${step}`, `HTTP ${response.status} ${JSON.stringify(response.body)?.slice(0, 200)}`);
    process.exit(1);
  }
  return response.body;
};

const EMAIL = `lm-e2e-csp-${STAMP}@nextwebspark.com`;
seeded("sign up", await api("/auth/signup", { method: "POST", body: { fullName: "Cass Policy", email: EMAIL, password: PASSWORD, termsAccepted: true } }));
seeded("verify", await api("/auth/verify", { method: "POST", body: { token: tokenFor(EMAIL, "verify") } }));
let token = seeded("sign in", await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).accessToken;
seeded("found the workspace", await api("/onboarding/workspace", { method: "POST", token, body: { mode: "COMPANY", name: `CSP SPA ${STAMP}` } }));
token = seeded("sign in to the workspace", await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).accessToken;
const clientId = seeded("add a business unit", await api("/clients", { method: "POST", token, body: { customName: `Policy Holding ${STAMP}`, customDomain: `policyholding${STAMP}.example` } })).id;
const PROJECT = seeded("open a position", await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).id;
const AURORA = seeded("capture a company", await api(`/projects/${PROJECT}/triage/capture`, { method: "POST", token, body: { companyName: "Aurora Holdings", status: "inUniverse", industry: "Financial Services", companyCountry: "United Arab Emirates", companyCity: "Dubai", source: "manual" } })).id;
seeded("map an executive", await api(`/projects/${PROJECT}/candidates`, { method: "POST", token, body: { triageCompanyId: AURORA, fullName: "Fatima Al Mansoori", title: "Group CFO", locationCountry: "United Arab Emirates", locationCity: "Dubai", source: "manual" } }));
// The report's globe draws only once a country is placed, and placing one is Mapbox's geocoder, so it needs the token.
const mapConfigured = Boolean((await api("/talent-map/config", { token })).body?.publicToken);

// --- the screens ----------------------------------------------------------------

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const violations = [];
const forbidden = [];
await page.addInitScript(() => {
  document.addEventListener("securitypolicyviolation", (event) => {
    window.__cspViolations = window.__cspViolations ?? [];
    window.__cspViolations.push(`${event.effectiveDirective} blocked ${event.blockedURI || "inline"}`);
  });
});
page.on("console", (message) => {
  if (message.type() === "error" && /Content Security Policy/i.test(message.text())) violations.push(message.text().slice(0, 240));
});
page.on("request", (request) => { if (FORBIDDEN_HOSTS.test(request.url())) forbidden.push(request.url()); });

const policy = (await page.goto(`${WEB}/login`)).headers()["content-security-policy"] ?? "";
policy.includes("script-src 'self'")
  ? pass("TP1", "the page arrives with the full policy")
  : fail("TP1", "the page arrives with the full policy", `got [${policy}]`);

await fillSignIn(page, EMAIL, PASSWORD);
await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });

const SCREENS = [
  ["TP2", "the projects list", "/"],
  ["TP3", "the position brief", `/projects/${PROJECT}`],
  ["TP4", "the universe", `/projects/${PROJECT}/companies/universe`],
  ["TP5", "the report's market chapter (Mapbox hub globe)", `/projects/${PROJECT}/reports?chapter=market`],
  ["TP6", "the candidates page", "/candidates"],
  ["TP7", "the signature editor and its preview frame", "/settings/signature"],
  ["TP8", "the booking page", "/book/no-such-link"],
  ["TP9", "the OAuth consent screen", "/oauth/consent?client_id=nobody"],
  ["TP10", "the privacy page", "/privacy/no-such-token"],
];
for (const [id, what, path] of SCREENS) {
  violations.length = 0;
  try {
    await page.goto(`${WEB}${path}`);
    await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1500);
    const seen = [...violations, ...(await page.evaluate(() => window.__cspViolations ?? []))];
    seen.length === 0 ? pass(id, `${what} opens with no CSP violation`) : fail(id, `${what} opens with no CSP violation`, seen.join(" | "));
    if (mapConfigured && path.includes("chapter=market")) {
      const drawn = await page.locator("canvas.mapboxgl-canvas").first().waitFor({ timeout: 20000 }).then(() => true, () => false);
      drawn ? pass(`${id}.1`, "the hub globe drew its canvas") : fail(`${id}.1`, "the hub globe drew its canvas", "no canvas.mapboxgl-canvas");
      await page.waitForTimeout(3000);
      const late = await page.evaluate(() => window.__cspViolations ?? []);
      late.length === 0 ? pass(`${id}.2`, "its tiles and worker load inside the policy") : fail(`${id}.2`, "its tiles and worker load inside the policy", late.join(" | "));
    }
  } catch (error) {
    fail(id, what, `threw: ${error.message.split("\n")[0]}`);
  }
}

// JetBrains Mono loads only where a figure is set in it, so Geist, which every page uses, is the one to ask about.
const geistLoaded = await page.evaluate(async () => {
  await document.fonts.ready;
  return [...document.fonts].some((face) => face.family.replaceAll('"', "") === "Geist Variable" && face.status === "loaded");
});
geistLoaded ? pass("TP11", "Geist loads from this origin") : fail("TP11", "Geist loads from this origin", "no loaded Geist Variable face");

forbidden.length === 0
  ? pass("TP12", "no page reached a font host or an error reporter")
  : fail("TP12", "no page reached a font host or an error reporter", [...new Set(forbidden)].join(" "));

await browser.close();
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
