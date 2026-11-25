// The platform console's door, in a browser: who is offered the Platform item, and where /platform sends
// everyone else. Reads the cast api/22-platform-console.sh left in platform.env, so that script runs first.
//
// The super admin here has not set up two-factor: the item is theirs to see, and the console asks for the
// factor before it shows anything. The console's own work, with the factor on, is 22's.

import { chromium } from "playwright";
import { readFileSync, mkdirSync, appendFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { fillSignIn } from "./sign-in.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const RUN_DIR = process.env.RUN_DIR ?? join(HERE, "..", "results", "current");
const SHOTS = join(HERE, "screenshots");
const WEB = "http://localhost:5173";
const PASSWORD = "Passw0rd123";

mkdirSync(SHOTS, { recursive: true });

const cast = Object.fromEntries(
  readFileSync(join(RUN_DIR, "platform.env"), "utf8")
    .split("\n")
    .filter((line) => line.includes("="))
    .map((line) => {
      const index = line.indexOf("=");
      return [line.slice(0, index), line.slice(index + 1).replace(/\\(.)/g, "$1")];
    }),
);

let passed = 0;
let failed = 0;
const record = (id, result, detail) =>
  appendFileSync(join(RUN_DIR, "cases.tsv"), `${id}\t${result}\t${detail}\n`);
const pass = (id, what) => { passed++; console.log(`  \x1b[32mPASS\x1b[0m ${id.padEnd(7)} ${what}`); record(id, "PASS", what); };
const fail = (id, what, why) => { failed++; console.log(`  \x1b[31mFAIL\x1b[0m ${id.padEnd(7)} ${what}\n          \x1b[2m${why}\x1b[0m`); record(id, "FAIL", `${what} -- ${why}`); };
const section = (title) => console.log(`\n\x1b[1;36m== ${title}\x1b[0m`);
const check = (id, what, expected, actual) =>
  String(expected) === String(actual) ? pass(id, what) : fail(id, what, `expected [${expected}] got [${actual}]`);

const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});

async function signIn(email, label) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  await page.goto(`${WEB}/login`);
  await fillSignIn(page, email, PASSWORD);
  await page.waitForTimeout(3000);
  await page.screenshot({ path: join(SHOTS, `platform-${label}-home.png`), fullPage: true });
  return page;
}

// Whether the account menu, once open, offers Platform.
async function offersPlatform(page) {
  await page.getByRole("button", { name: /^Account: / }).click();
  await page.getByRole("menuitem", { name: /sign out/i }).waitFor({ timeout: 5000 });
  const offered = await page.getByRole("menuitem", { name: /^Platform$/ }).count();
  await page.keyboard.press("Escape");
  return offered > 0;
}

async function landsOn(page, path) {
  await page.goto(`${WEB}${path}`);
  await page.waitForTimeout(2000);
  return new URL(page.url()).pathname;
}

try {
  section("PL1  who is offered the platform console");

  const staff = await signIn(cast.PLATFORM_UNENROLLED_EMAIL, "super-admin");
  check("PL1.1", "a super admin's account menu offers Platform", true, await offersPlatform(staff));
  check("PL1.2", "and /platform stays open to them", true, (await landsOn(staff, "/platform")).startsWith("/platform"));
  check("PL1.3", "where, with no two-factor yet, the console asks for it before showing anything", true,
    /Set up two-factor to use the platform console/.test(await staff.locator("body").innerText()));
  await staff.screenshot({ path: join(SHOTS, "platform-super-admin-two-factor.png"), fullPage: true });

  const outsiders = [
    ["PL1.4", "a workspace admin", cast.PLATFORM_ADMIN_EMAIL, "admin"],
    ["PL1.6", "a member", cast.PLATFORM_MEMBER_EMAIL, "member"],
    ["PL1.8", "a client", cast.PLATFORM_CLIENT_EMAIL, "client"],
  ];
  for (const [id, who, email, label] of outsiders) {
    const page = await signIn(email, label);
    check(id, `${who} is offered no Platform item`, false, await offersPlatform(page));
    check(id.replace(/\d$/, (digit) => String(Number(digit) + 1)), `and ${who} typing /platform/prompts is sent home`, "/",
      await landsOn(page, "/platform/prompts"));
  }
} catch (error) {
  fail("SPA", "the browser run completed", String(error).slice(0, 400));
} finally {
  await browser.close();
}

console.log(`\n\x1b[1;36m---- spa/platform.mjs: \x1b[32m${passed} passed\x1b[0m, \x1b[31m${failed} failed\x1b[0m`);

process.exitCode = failed > 0 ? 1 : 0;
