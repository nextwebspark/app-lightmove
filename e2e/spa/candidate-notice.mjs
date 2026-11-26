// Uncava's own notice to executives (#1490, B2/B6) in a real browser against a real API: once it is published, the
// privacy notice email and the public privacy page link it, and the person's record holds the version sent.
//
// Needs `LEGAL_CANDIDATE_NOTICE_VERSION=2026-12 LEGAL_CANDIDATE_NOTICE_URL=https://uncava.com/executive-privacy
// DATA_PROTECTION_ENABLED=true SUPPRESSION_LIST_ENABLED=true npm run dev`; mail prints to the API console, and
// PG_CONTAINER names the database container (`lm-dev-pg` by default).
//
//   API_LOG=<the API's console log> node e2e/spa/candidate-notice.mjs

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
const NOTICE = "https://uncava.com/executive-privacy";
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
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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

const sql = (query) =>
  execFileSync("docker", ["exec", PG_CONTAINER, "psql", "-U", "postgres", "-d", "lightmove", "-tAc", query], {
    encoding: "utf8",
  }).trim();

const EMAIL = `lm-e2e-notice-${STAMP}@${DOMAIN}`;
const NOOR = `noor-${STAMP}@example.com`;
await api("/auth/signup", { method: "POST", body: { fullName: "Nadia Notice", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await sleep(1500);
const verify = linkFor(EMAIL, /http:\/\/localhost:5173\/auth\/verify\?token=[A-Za-z0-9_%.~-]+/);
await api("/auth/verify", { method: "POST", body: { token: verify.split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "AGENCY", name: `Notice SPA ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/workspace/candidate-privacy", {
  method: "PUT", token,
  body: { controllerName: "Meridian Search Partners FZ-LLC", privacyContactEmail: "privacy@meridian.example",
    noticeUrl: "https://meridian.example/privacy", defaultLawfulBasis: "legitimate_interest" },
});
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Notice Unit ${STAMP}` } })).body.id;
const position = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Chief Financial Officer" } })).body.id;
const PERSON = (await api(`/projects/${position}/candidates`, {
  method: "POST", token,
  body: { fullName: "Noor Haddad", linkedinUrl: `https://www.linkedin.com/in/noor-${STAMP}/`, emails: [{ value: NOOR }] },
})).body.personId;

console.log("\nUncava's notice to executives (#1490)");
let pageLink = null;
await step("CN.1", "the privacy notice email links Uncava's notice", async () => {
  const sent = await api(`/candidates/${PERSON}/privacy-notice`, { method: "POST", token });
  check("CN.1a", "the notice is sent", 200, sent.status);
  await sleep(2500);
  const log = readFileSync(API_LOG, "utf8");
  check("CN.1b", "the email carries the link", true, log.includes(`How Uncava handles your details: ${NOTICE}`));
  pageLink = linkFor(NOOR, /http:\/\/localhost:5173\/privacy\/[A-Za-z0-9_-]+/);
});

await step("CN.2", "the person's record holds the version sent", async () => {
  check("CN.2", "uncava_notice_version", "2026-12",
    sql(`select uncava_notice_version from app_lm_person where id = '${PERSON}'`));
});

const browser = await chromium.launch();
const outside = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
try {
  await step("CN.3", "the public privacy page links it", async () => {
    await outside.goto(pageLink.replace("http://localhost:5173", WEB));
    const link = outside.getByRole("link", { name: "How Uncava handles your details" });
    await link.waitFor({ timeout: 15000 });
    check("CN.3", "the link's target", NOTICE, await link.getAttribute("href"));
    await outside.screenshot({ path: join(SHOTS, "candidate-notice-page.png") });
  });
} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
