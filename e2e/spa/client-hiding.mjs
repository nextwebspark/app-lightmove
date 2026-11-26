// A client stops seeing someone who is restricted (#1488, D2), in a real browser against a real API: the staff
// Pipeline locks the client switch with the reason, the pipeline row says why, and the client seat reads them no more.
//
// Needs `DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev`; mail prints to the API console.
// Skips itself where data protection is off, as on the matrix's e2e profile, whose restriction route is a 404.
//
//   API_LOG=<the API's console log> node e2e/spa/client-hiding.mjs

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
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

const EMAIL = `lm-e2e-hiding-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Hana Hiding", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
const verify = linkFor(EMAIL, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
await api("/auth/verify", { method: "POST", body: { token: verify.split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "AGENCY", name: `Hiding SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Hiding Unit ${STAMP}` } })).body.id;
const position = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;
const filed = (await api(`/projects/${position}/candidates`, {
  method: "POST", token,
  body: { fullName: "Rania Odeh", linkedinUrl: `https://www.linkedin.com/in/rania-${STAMP}/` },
})).body;
await api(`/projects/${position}/candidates`, { method: "POST", token, body: { fullName: "Samir Haddad" } });

const REP_EMAIL = `lm-e2e-hiding-rep-${STAMP}@${DOMAIN}`;
await api(`/projects/${position}/representatives/invitations`, {
  method: "POST", token, body: { fullName: "Cora Client", position: "Group CFO", email: REP_EMAIL },
});
await new Promise((resolve) => setTimeout(resolve, 1500));
const invite = linkFor(REP_EMAIL, /http:\/\/localhost:5173\/auth\/accept-invite\?token=[A-Za-z0-9_%.~-]+/);
const clientToken = (await api("/onboarding/accept-invitation-signup", {
  method: "POST",
  body: { token: decodeURIComponent(invite.split("token=")[1]), fullName: "Cora Client", password: PASSWORD, termsAccepted: true },
})).body.accessToken;
const clientReads = async () => JSON.stringify((await api(`/projects/${position}/candidates`, { token: clientToken })).body);

console.log("\nHidden from the client (#1488)");
check("CH.0", "the client seat reads her before any restriction", true, (await clientReads()).includes("Rania Odeh"));
const restricted = await api(`/candidates/${filed.personId}/processing-restriction`, {
  method: "PUT", token, body: { restricted: true, reason: "Asked what we hold about her" },
});
if (restricted.status === 404) {
  console.log("\n\x1b[2mSKIP    data protection is off on this stack: run it with DATA_PROTECTION_ENABLED=true\x1b[0m");
  record("CH.1", "SKIP", "data protection is off on this stack");
  process.exit(0);
}
await step("CH.1", "a restriction hides them from the client, and the pipeline row says why", async () => {
  check("CH.1a", "the restriction is saved", 200, restricted.status);
  const pipeline = (await api(`/projects/${position}/pipeline`, { token })).body;
  const row = JSON.stringify(pipeline).includes("processing_restricted");
  check("CH.1b", "the pipeline row carries processing_restricted", true, row);
  const seen = await clientReads();
  check("CH.1c", "the client seat no longer reads her", false, seen.includes("Rania Odeh"));
  check("CH.1d", "the client seat still reads her colleague", true, seen.includes("Samir Haddad"));
});

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
try {
  await step("CH.2", "the Pipeline card locks the client switch with Hidden from {client}", async () => {
    await page.goto(`${WEB}/login`);
    await fillSignIn(page, EMAIL, PASSWORD);
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/projects/${position}/companies/universe?view=pipeline`);
    const card = page.getByRole("article", { name: "Rania Odeh" });
    await card.waitFor({ timeout: 15000 });
    await card.getByText(/Hidden from/).waitFor({ timeout: 5000 });
    const locked = card.getByRole("switch", { name: /Visible to client: Rania Odeh/ });
    check("CH.2", "the switch is locked", "true", await locked.getAttribute("aria-disabled"));
    await page.waitForTimeout(400);
    await page.screenshot({ path: join(SHOTS, "client-hiding-pipeline.png") });
  });

  await step("CH.3", "lifting the restriction gives the team its switch back", async () => {
    await api(`/candidates/${filed.personId}/processing-restriction`, { method: "PUT", token, body: { restricted: false } });
    const pipeline = (await api(`/projects/${position}/pipeline`, { token })).body;
    check("CH.3", "no hidden reason left", false, JSON.stringify(pipeline).includes("processing_restricted"));
    check("CH.3b", "the client seat reads her again", true, (await clientReads()).includes("Rania Odeh"));
  });
} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
