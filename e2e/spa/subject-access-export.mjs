// Export personal data (#919) in a real browser against a real API: an admin opens a person on Candidates, exports
// everything held on them from the drawer's ⋯, and the zip that downloads holds the readable page, the JSON and the
// latest file of each document, with the audit line recorded by id and counts.
//
// Needs `npm run dev` (or its two halves) with DATA_PROTECTION_ENABLED on, which `ops/dev/api.sh` sets. Reads the
// zip with Python's zipfile, so `python` must be on the PATH.
//
//   API_LOG=<the API's console log> node e2e/spa/subject-access-export.mjs

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

function linkFor(address, kind = "verify") {
  let mine = false;
  let found = null;
  for (const line of readFileSync(API_LOG, "utf8").split("\n")) {
    if (line.includes("To:")) mine = line.includes(address);
    if (mine) {
      const match = line.match(new RegExp(`http://localhost:5173/auth/${kind}\\?token=[A-Za-z0-9_%.~-]+`));
      if (match) found = match[0];
    }
  }
  return found;
}

const api = async (path, { method = "GET", body, token, form } = {}) => {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(form ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
};

/** Each entry of a zip as text (binary entries as their size), read with Python's zipfile. */
const unzip = (path) => JSON.parse(execFileSync("python", ["-I", "-c", `
import json, sys, zipfile
out = {}
with zipfile.ZipFile(sys.argv[1]) as z:
    for name in z.namelist():
        data = z.read(name)
        try:
            out[name] = data.decode("utf-8")
        except UnicodeDecodeError:
            out[name] = "<%d bytes>" % len(data)
print(json.dumps(out))
`, path]).toString());

// --- the seed -------------------------------------------------------------------------

const EMAIL = `lm-e2e-sar-${STAMP}@${DOMAIN}`;
await api("/auth/signup", { method: "POST", body: { fullName: "Alok Admin", email: EMAIL, password: PASSWORD, termsAccepted: true } });
await new Promise((resolve) => setTimeout(resolve, 1500));
await api("/auth/verify", { method: "POST", body: { token: linkFor(EMAIL, "verify").split("token=")[1] } });
let token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
await api("/onboarding/workspace", { method: "POST", token, body: { mode: "COMPANY", name: `Subject Access ${STAMP}` } });
token = (await api("/auth/login", { method: "POST", body: { email: EMAIL, password: PASSWORD } })).body.accessToken;
const clientId = (await api("/clients", { method: "POST", token, body: { customName: `Treasury Unit ${STAMP}` } })).body.id;
const PROJECT = (await api("/projects", { method: "POST", token, body: { clientId, positionTitle: "Group Treasurer" } })).body.id;
const added = await api(`/projects/${PROJECT}/candidates`, {
  method: "POST", token,
  body: {
    fullName: "Noor Haddad", title: "Finance Director", employerName: "Aldar",
    linkedinUrl: `https://www.linkedin.com/in/noor-haddad-${STAMP}/`, emails: [{ value: `noor-${STAMP}@example.com` }],
  },
});
if (added.status !== 201) throw new Error(`seeding Noor answered ${added.status}: ${JSON.stringify(added.body)}`);
const PERSON = added.body.personId;
await api(`/candidates/${PERSON}/notes`, { method: "POST", token, body: { body: "Said <script>alert(1)</script> on the call" } });
for (const text of ["first", "second"]) {
  const form = new FormData();
  form.append("file", new Blob([`%PDF-1.7\n${text}`], { type: "application/pdf" }), "Noor_Haddad_CV.pdf");
  const uploaded = await api(`/candidates/${PERSON}/documents`, { method: "POST", token, form });
  if (uploaded.status !== 201) throw new Error(`uploading the CV answered ${uploaded.status}`);
}

// --- the browser ----------------------------------------------------------------------

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
const page = await context.newPage();
const shot = (name) => page.screenshot({ path: join(SHOTS, `subject-access-${name}.png`), fullPage: true });

try {
  console.log("\n\x1b[1;36m== SA1  Export personal data\x1b[0m");
  let zipPath = null;
  await step("SA1.1", "an admin finds Export personal data in the person's ⋯ and reads the dialog", async () => {
    await page.goto(`${WEB}/login`);
    await page.getByPlaceholder("you@firm.com").fill(EMAIL);
    await page.getByPlaceholder("••••••••").fill(PASSWORD);
    await page.getByRole("button", { name: "Continue", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 20000 });
    await page.goto(`${WEB}/candidates?person=${PERSON}`);
    await page.getByRole("button", { name: "More for Noor Haddad" }).click({ timeout: 20000 });
    await shot("menu");
    await page.getByRole("menuitem", { name: /Export personal data/ }).click();
    const dialog = page.getByRole("dialog", { name: /Export everything held on Noor Haddad/ });
    await dialog.waitFor({ timeout: 10000 });
    await page.waitForTimeout(400); // the dialog fades in; a shot taken at once shows it half drawn
    await shot("dialog");
    check("SA1.1", "the dialog asks the admin to check it before sending",
      true, (await dialog.getByRole("note").innerText()).includes("Check it before you send it."));
  });

  await step("SA1.2", "Download saves the zip and says it is recorded", async () => {
    const [download] = await Promise.all([
      page.waitForEvent("download", { timeout: 20000 }),
      page.getByRole("button", { name: "Download", exact: true }).click(),
    ]);
    check("SA1.2", "the file is named after the person and the day",
      true, /^uncava-subject-access-noor-haddad-\d{4}-\d{2}-\d{2}\.zip$/.test(download.suggestedFilename()));
    zipPath = join(RUN_DIR, download.suggestedFilename());
    await download.saveAs(zipPath);
    await page.getByText("Exported everything held on Noor Haddad", { exact: false }).first().waitFor({ timeout: 10000 });
    pass("SA1.2b", "the toast says it was recorded in the audit log");
    await shot("toast");
  });

  await step("SA1.3", "the zip holds the page, the JSON and only the latest CV", async () => {
    const entries = unzip(zipPath);
    const names = Object.keys(entries);
    check("SA1.3", "README, person.json and person.html are in it",
      true, ["README.txt", "person.json", "person.html"].every((name) => names.includes(name)));
    const documents = names.filter((name) => name.startsWith("documents/"));
    check("SA1.3b", "one document file, the second version", "1:true",
      `${documents.length}:${(entries[documents[0]] ?? "").endsWith("second")}`);
    const person = JSON.parse(entries["person.json"]);
    check("SA1.3c", "the contact carries its source", "MANUAL", person.data.contacts[0]?.source);
    check("SA1.3d", "both CV versions are listed", 2, person.data.documentVersions.length);
    check("SA1.3e", "the note's script is escaped on the page", true,
      entries["person.html"].includes("&lt;script&gt;alert(1)&lt;/script&gt;") && !entries["person.html"].includes("<script>"));
    check("SA1.3f", "the page names who holds the data", true,
      entries["person.html"].includes(`Held by Subject Access ${STAMP}`));
  });

  await step("SA1.4", "the audit log records the export by id and counts, never the name", async () => {
    const metadata = sql(`SELECT metadata::text FROM app_lm_audit_event
                          WHERE event_type = 'PERSON_SUBJECT_ACCESS_EXPORTED' AND target_id = '${PERSON}'`);
    check("SA1.4", "one line, counting one document and no name", true,
      metadata.includes('"documents": 1') && !/noor/i.test(metadata));
    await page.goto(`${WEB}/settings/security?tab=audit-log`);
    await page.getByText("Exported everything held on a candidate").first().waitFor({ timeout: 15000 });
    pass("SA1.4b", "Settings → Security → Audit log phrases it");
    await shot("audit-log");
  });
} finally {
  await browser.close();
  console.log(`\n${passed} passed, ${failed} failed`);
  process.exitCode = failed > 0 ? 1 : 0;
}
