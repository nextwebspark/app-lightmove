/**
 * Keyboard safety of the overlays: a dialog or drawer takes focus when it opens, Tab and Shift-Tab never
 * leave it, and closing it hands focus back to whatever opened it.
 *
 * Stubs `/api/v1` like `responsive.mjs`, so it runs against a bare `npm run dev -w apps/web`.
 *
 *   node e2e/spa/a11y-dialogs.mjs [baseUrl]
 */

import { appendFileSync } from "node:fs";
import { chromium } from "playwright";
import { payloadFor } from "./responsive-fixtures.mjs";

const WEB = process.argv[2] ?? process.env.WEB ?? "http://localhost:5173";
const EXECUTABLE_PATH = process.env.CHROMIUM_PATH;
const PRESSES = 30;

let passed = 0;
const failures = [];
const CASES = process.env.RUN_DIR ? `${process.env.RUN_DIR}/cases.tsv` : null;

const check = (id, what, expected, actual) => {
  const ok = expected === actual;
  if (CASES) appendFileSync(CASES, `${id}\t${ok ? "PASS" : "FAIL"}\t${what}${ok ? "" : ` -- expected ${expected}, got ${actual}`}\n`);
  if (ok) {
    passed += 1;
  } else {
    failures.push(`${id}  ${what}\n      expected ${expected}, got ${actual}`);
  }
  console.log(`  ${ok ? "ok  " : "FAIL"} ${id}  ${what}`);
};

const focusInDialog = (page) => page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]'));
const focusIsField = (page) => page.evaluate(() => /^(INPUT|SELECT|TEXTAREA)$/.test(document.activeElement?.tagName ?? ""));

/** Presses the key `PRESSES` times and answers the first press that left the dialog, or null. */
async function escapeAfter(page, key) {
  for (let press = 1; press <= PRESSES; press++) {
    await page.keyboard.press(key);
    if (!(await focusInDialog(page))) return press;
  }
  return null;
}

/** Escape until the dialog is gone: the first press may only close a field's open suggestion list. */
async function closeWithEscape(page) {
  for (let press = 0; press < 3 && (await page.getByRole("dialog").count()) > 0; press++) {
    await page.keyboard.press("Escape");
    await page.waitForTimeout(150);
  }
}

const browser = await chromium.launch(EXECUTABLE_PATH ? { executablePath: EXECUTABLE_PATH } : {});

try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.route("**/api/v1/**", async (route) => {
    const { pathname, search } = new URL(route.request().url());
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(payloadFor(pathname, search)),
    });
  });
  const page = await context.newPage();

  console.log("\n── New position (modal) ──");
  await page.goto(`${WEB}/`, { waitUntil: "networkidle" });
  const opener = page.getByRole("button", { name: "New position" }).first();
  await opener.click();
  await page.getByRole("dialog", { name: "New position" }).waitFor();
  check("a11y/modal", "opens on its first field", true, await focusIsField(page));
  check("a11y/modal", `Tab ×${PRESSES} stays inside`, null, await escapeAfter(page, "Tab"));
  check("a11y/modal", `Shift+Tab ×${PRESSES} stays inside`, null, await escapeAfter(page, "Shift+Tab"));
  await closeWithEscape(page);
  check("a11y/modal", "Escape closes it", 0, await page.getByRole("dialog").count());
  check("a11y/modal", "focus returns to New position", true, await opener.evaluate((el) => el === document.activeElement));

  console.log("\n── Position side panel (drawer) ──");
  const row = page.locator('[role="row"][tabindex="0"]').first();
  await row.focus();
  await page.keyboard.press("Enter");
  await page.getByRole("dialog").waitFor();
  check("a11y/drawer", "a row opens it from the keyboard", 1, await page.getByRole("dialog").count());
  check("a11y/drawer", "takes focus on open", true, await focusInDialog(page));
  check("a11y/drawer", `Tab ×${PRESSES} stays inside`, null, await escapeAfter(page, "Tab"));
  check("a11y/drawer", `Shift+Tab ×${PRESSES} stays inside`, null, await escapeAfter(page, "Shift+Tab"));
  await closeWithEscape(page);
  check("a11y/drawer", "Escape closes it", 0, await page.getByRole("dialog").count());
  check("a11y/drawer", "focus returns to the row", true, await row.evaluate((el) => el === document.activeElement));

  await context.close();
} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failures.length} failed`);
if (failures.length) {
  console.log(`\n${failures.join("\n")}`);
  process.exit(1);
}
