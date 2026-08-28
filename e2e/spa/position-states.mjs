/**
 * The Position screen in each of the states publication puts it in, at desktop width.
 *
 *   node e2e/spa/position-states.mjs [baseUrl]
 *
 * Stubs `/api/v1` like the responsive sweep, so it needs `npm run dev -w apps/web` and nothing else.
 */

import { mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { payloadFor } from "./responsive-fixtures.mjs";

const WEB = process.argv[2] ?? process.env.WEB ?? "http://localhost:5173";
const EXECUTABLE_PATH = process.env.CHROMIUM_PATH;
const SHOTS = new URL("./screenshots/position-states/", import.meta.url).pathname;

async function stubApi(context, { published }) {
  await context.route("**/api/v1/**", async (route) => {
    const { pathname, search } = new URL(route.request().url());
    const payload = payloadFor(pathname, search);
    const brief =
      /\/projects\/[^/]+\/position/.test(pathname) && payload
        ? {
            ...payload,
            publication: published
              ? { publishedAt: "2026-08-28T09:12:00Z", publishedBy: "Ada Lovelace-Kensington" }
              : { publishedAt: null, publishedBy: null },
          }
        : payload;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(brief),
    });
  });
}

async function shoot(browser, { published, name, act }) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await stubApi(context, { published });
  const page = await context.newPage();
  page.on("pageerror", (error) => console.log(`  page error: ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") console.log(`  console: ${message.text()}`);
  });
  await page.goto(`${WEB}/projects/proj-1`, { waitUntil: "networkidle" });
  await page.waitForSelector("aside");
  if (act) await act(page);
  await page.waitForTimeout(250);
  await page.screenshot({ path: `${SHOTS}${name}.png`, fullPage: false });
  const rail = page.locator("aside");
  const buttons = await rail.getByRole("button").allInnerTexts();
  console.log(`${name}\n  rail: ${buttons.filter(Boolean).join(" | ")}`);
  await context.close();
}

mkdirSync(SHOTS, { recursive: true });
const browser = await chromium.launch(EXECUTABLE_PATH ? { executablePath: EXECUTABLE_PATH } : {});

await shoot(browser, { published: false, name: "1-draft-step-one" });
await shoot(browser, {
  published: false,
  name: "2-draft-review",
  act: (page) => page.getByRole("button", { name: /Review & publish/ }).click(),
});
await shoot(browser, { published: true, name: "3-published-read-back" });
await shoot(browser, {
  published: true,
  name: "4-published-editing",
  act: (page) => page.getByRole("button", { name: "Edit position" }).click(),
});
await shoot(browser, {
  published: true,
  name: "5-published-editing-section",
  act: async (page) => {
    await page.getByRole("button", { name: "Edit position" }).click();
    await page.getByRole("button", { name: "Edit" }).nth(1).click();
  },
});

await browser.close();
console.log(`\nshots in ${SHOTS}`);
