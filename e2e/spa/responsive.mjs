/**
 * The responsive sweep: every screen at phone, tablet and desktop width.
 *
 * Unlike its siblings this driver stubs `/api/v1` rather than needing the stack, so it runs against a
 * bare `npm run dev -w apps/web`.
 *
 *   node e2e/spa/responsive.mjs [baseUrl]
 */

import { mkdirSync } from "node:fs";
import { chromium } from "playwright";
import { payloadFor } from "./responsive-fixtures.mjs";

const WEB = process.argv[2] ?? process.env.WEB ?? "http://localhost:5173";

/** Set where the sandbox ships a Chromium that the pinned Playwright would otherwise re-download. */
const EXECUTABLE_PATH = process.env.CHROMIUM_PATH;
const SHOTS = new URL("./screenshots/responsive/", import.meta.url).pathname;

const VIEWPORTS = [
  { name: "phone", width: 390, height: 844 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "desktop", width: 1440, height: 900 },
];

const ROUTES = [
  { path: "/login", name: "login", anonymous: true },
  { path: "/signup", name: "signup", anonymous: true },
  { path: "/forgot-password", name: "forgot-password", anonymous: true },
  { path: "/", name: "projects-my" },
  { path: "/all", name: "projects-all" },
  { path: "/clients", name: "clients" },
  { path: "/team", name: "team" },
  { path: "/settings/profile", name: "settings-profile" },
  { path: "/settings/security", name: "settings-security" },
  { path: "/settings/general", name: "settings-general" },
  { path: "/settings/members", name: "settings-members" },
  { path: "/projects/proj-1", name: "project-position" },
  { path: "/projects/proj-1/strategy", name: "project-strategy" },
  { path: "/projects/proj-1/triage", name: "project-triage" },
  { path: "/projects/proj-1/reports", name: "project-reports" },
  { path: "/projects/proj-1/team", name: "project-team" },
];

let passed = 0;
const failures = [];

const check = (id, what, expected, actual) => {
  const ok = expected === actual;
  if (ok) {
    passed += 1;
  } else {
    failures.push(`${id}  ${what}\n      expected ${expected}, got ${actual}`);
  }
  console.log(`  ${ok ? "ok  " : "FAIL"} ${id}  ${what}`);
};

/** `signedIn: false` refuses the refresh, which is what puts the app on the signed-out screens. */
async function stubApi(context, { signedIn = true } = {}) {
  await context.route("**/api/v1/**", async (route) => {
    const { pathname } = new URL(route.request().url());

    if (!signedIn && pathname.endsWith("/auth/refresh")) {
      await route.fulfill({
        status: 401,
        contentType: "application/problem+json",
        body: JSON.stringify({ type: "about:blank", status: 401, code: "UNAUTHENTICATED" }),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(payloadFor(pathname)),
    });
  });
}

const browser = await chromium.launch(
  EXECUTABLE_PATH ? { executablePath: EXECUTABLE_PATH } : {},
);
mkdirSync(SHOTS, { recursive: true });

try {
  for (const viewport of VIEWPORTS) {
    console.log(`\n── ${viewport.name} (${viewport.width}x${viewport.height}) ──`);

    const contexts = { anonymous: null, authenticated: null };
    for (const [kind, signedIn] of [["anonymous", false], ["authenticated", true]]) {
      const created = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        deviceScaleFactor: 1,
      });
      await stubApi(created, { signedIn });
      contexts[kind] = created;
    }

    let crash = null;
    const pages = {};
    for (const kind of ["anonymous", "authenticated"]) {
      pages[kind] = await contexts[kind].newPage();
      pages[kind].on("pageerror", (error) => {
        crash ??= String(error).split("\n")[0];
      });
    }

    for (const route of ROUTES) {
      const page = pages[route.anonymous ? "anonymous" : "authenticated"];
      crash = null;
      await page.goto(`${WEB}${route.path}`, { waitUntil: "networkidle" });
      await page.waitForTimeout(350);

      // A blank screen cannot overflow, so it would pass the check below for the wrong reason.
      check(`${route.name}/${viewport.name}`, `renders without crashing${crash ? ` — ${crash}` : ""}`, null, crash);
      const rendered = (await page.locator("#root").innerText()).trim().length;
      check(`${route.name}/${viewport.name}`, `renders content (${rendered} chars)`, true, rendered > 40);

      const landed = new URL(page.url()).pathname;
      if (!route.anonymous && landed !== route.path) {
        check(`${route.name}/${viewport.name}`, `reaches ${route.path}`, route.path, landed);
        continue;
      }

      // The whole point: nothing may push the document sideways.
      const overflow = await page.evaluate(() => {
        const doc = document.documentElement;
        const widest = [...document.querySelectorAll("body *")]
          .filter((el) => el.getBoundingClientRect().right > doc.clientWidth + 1)
          .slice(0, 3)
          .map((el) => `${el.tagName.toLowerCase()}.${(el.className || "").toString().slice(0, 60)}`);
        return { scrollWidth: doc.scrollWidth, clientWidth: doc.clientWidth, widest };
      });

      const fits = overflow.scrollWidth <= overflow.clientWidth + 1;
      check(
        `${route.name}/${viewport.name}`,
        `no horizontal overflow${fits ? "" : ` — ${overflow.widest.join(", ")}`}`,
        true,
        fits,
      );

      await page.screenshot({
        path: `${SHOTS}${route.name}-${viewport.name}.png`,
        fullPage: false,
      });
    }

    for (const kind of ["anonymous", "authenticated"]) await contexts[kind].close();
  }

  console.log("\n── nav drawer ──");
  for (const viewport of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
    });
    await stubApi(context);
    const page = await context.newPage();
    await page.goto(`${WEB}/`, { waitUntil: "networkidle" });
    await page.waitForTimeout(350);

    const menu = page.getByRole("button", { name: "Open navigation" });
    const nav = page.locator("#app-nav");
    const onScreen = async () => (await nav.boundingBox())?.x >= 0;

    if (viewport.width >= 1024) {
      check(`drawer/${viewport.name}`, "no hamburger at lg and up", false, await menu.isVisible());
      check(`drawer/${viewport.name}`, "rail is in flow", true, await onScreen());
    } else {
      check(`drawer/${viewport.name}`, "hamburger is offered", true, await menu.isVisible());
      check(`drawer/${viewport.name}`, "rail starts off-screen", false, await onScreen());
      await menu.click();
      await page.waitForTimeout(350);
      check(`drawer/${viewport.name}`, "hamburger opens the rail", true, await onScreen());
      await page.keyboard.press("Escape");
      await page.waitForTimeout(350);
      check(`drawer/${viewport.name}`, "Escape closes it", false, await onScreen());
    }
    await context.close();
  }
  // The rail carries a z-index for its drawer mode; unreset at `lg` it floats above an open scrim.
  console.log("\n── drawer scrim ──");
  {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await stubApi(context);
    const page = await context.newPage();
    await page.goto(`${WEB}/`, { waitUntil: "networkidle" });
    await page.waitForTimeout(350);
    await page.locator("table tbody tr").first().click();
    await page.waitForTimeout(400);

    const dimmed = await page.evaluate(() => {
      const nav = document.querySelector("#app-nav");
      const box = nav.getBoundingClientRect();
      const topmost = document.elementFromPoint(box.x + box.width / 2, box.y + 120);
      return !nav.contains(topmost) && topmost !== nav;
    });
    check("scrim/desktop", "the open drawer dims the nav rail too", true, dimmed);
    await context.close();
  }

} finally {
  await browser.close();
}

console.log(`\n${passed} passed, ${failures.length} failed`);
if (failures.length) {
  console.log(`\n${failures.join("\n")}`);
  process.exit(1);
}
