/**
 * The Position screen in each of the states publication puts it in, at desktop width.
 *
 *   node e2e/spa/position-states.mjs [baseUrl]
 *
 * Stubs `/api/v1` like the responsive sweep, so it needs `npm run dev -w apps/web` and nothing else.
 * The step lives in the URL (`?step=`), so each shot is a navigation rather than a click.
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

async function shoot(browser, { published, name, step }) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await stubApi(context, { published });
  const page = await context.newPage();
  page.on("pageerror", (error) => console.log(`  page error: ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error") console.log(`  console: ${message.text()}`);
  });
  const query = step ? `?step=${step}` : "";
  await page.goto(`${WEB}/projects/proj-1${query}`, { waitUntil: "networkidle" });
  await page.waitForSelector("aside");
  await page.waitForTimeout(250);
  await page.screenshot({ path: `${SHOTS}${name}.png`, fullPage: false });
  const rail = page.locator("aside");
  const steps = await rail.getByRole("link").allInnerTexts();
  const buttons = await rail.getByRole("button").allInnerTexts();
  console.log(`${name}\n  steps: ${steps.map((text) => text.replace(/\s+/g, " ").trim()).join(" | ")}\n  buttons: ${buttons.filter(Boolean).join(" | ")}`);
  await context.close();
}

mkdirSync(SHOTS, { recursive: true });
const browser = await chromium.launch(EXECUTABLE_PATH ? { executablePath: EXECUTABLE_PATH } : {});

await shoot(browser, { published: false, name: "1-draft-role-brief" });
await shoot(browser, { published: false, name: "2-draft-reporting", step: "reporting" });
await shoot(browser, { published: false, name: "3-draft-compensation", step: "compensation" });
await shoot(browser, { published: false, name: "4-draft-assessment", step: "assessment" });
await shoot(browser, { published: false, name: "5-draft-review", step: "review" });
await shoot(browser, { published: true, name: "6-published-opens-on-review" });

await browser.close();
console.log(`\nshots in ${SHOTS}`);
