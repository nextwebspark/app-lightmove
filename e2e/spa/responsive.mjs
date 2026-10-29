/**
 * The responsive sweep: every screen at phone, tablet and desktop width.
 *
 * Unlike its siblings this driver stubs `/api/v1` rather than needing the stack, so it runs against a
 * bare `npm run dev -w apps/web`.
 *
 *   node e2e/spa/responsive.mjs [baseUrl]
 */

import { appendFileSync, mkdirSync } from "node:fs";
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
  { path: "/settings/workspaces", name: "settings-workspaces" },
  { path: "/settings/general", name: "settings-general" },
  { path: "/settings/integrations", name: "settings-integrations" },
  { path: "/projects/proj-1", name: "project-position" },
  // One step of the brief renders at a time, so each is its own sweep: the org chart and the
  // weighting tables live past the first.
  { path: "/projects/proj-1?step=reporting", name: "project-position-reporting" },
  { path: "/projects/proj-1?step=compensation", name: "project-position-compensation" },
  { path: "/projects/proj-1?step=assessment", name: "project-position-assessment" },
  { path: "/projects/proj-1?step=review", name: "project-position-review" },
  { path: "/projects/proj-1/strategy", name: "project-strategy" },
  { path: "/projects/proj-1/companies/universe", name: "project-companies-universe" },
  { path: "/projects/proj-1/companies/universe?view=pipeline", name: "project-companies-pipeline" },
  { path: "/projects/proj-1/companies/declined", name: "project-companies-declined" },
  { path: "/projects/proj-1/reports", name: "project-reports" },
  // One chapter renders at a time, so each is its own sweep: the charts live past the first.
  { path: "/projects/proj-1/reports?chapter=market", name: "project-reports-market" },
  { path: "/projects/proj-1/reports?chapter=comp", name: "project-reports-remuneration" },
  { path: "/projects/proj-1/reports?chapter=dei", name: "project-reports-diversity" },
  { path: "/projects/proj-1/team", name: "project-team" },
  { path: "/projects/proj-1/outreach", name: "project-outreach" },
  { path: "/projects/proj-1/outreach?candidate=cand-1", name: "project-outreach-conversation" },
  { path: "/projects/proj-1/outreach/sequences", name: "project-outreach-sequences" },
  // Not screens anyone links to: an unknown URL and an unreadable project id render the not-found
  // page at the address that was asked for, so "reaches the path" is itself the assertion.
  { path: "/nowhere-in-particular", name: "not-found" },
  { path: "/projects/no-such-project", name: "not-found-project" },
];

let passed = 0;
const failures = [];

// Under run-all.sh every case also lands in the run's tally; run bare, it only prints.
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

/** `signedIn: false` refuses the refresh, which is what puts the app on the signed-out screens. */
async function stubApi(context, { signedIn = true } = {}) {
  await context.route("**/api/v1/**", async (route) => {
    const { pathname, search } = new URL(route.request().url());

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
      body: JSON.stringify(payloadFor(pathname, search)),
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
      if (!route.anonymous && landed !== new URL(route.path, WEB).pathname) {
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

  console.log("\n── nav: rail on a desktop, bottom bar and More on a phone ──");
  for (const viewport of VIEWPORTS) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
    });
    await stubApi(context);
    const page = await context.newPage();
    await page.goto(`${WEB}/`, { waitUntil: "networkidle" });
    await page.waitForTimeout(350);

    const bar = page.getByRole("navigation", { name: "Quick navigation" });
    const rail = page.locator("#app-nav");
    const more = page.getByRole("dialog", { name: "More" });

    if (viewport.width >= 1024) {
      check(`nav/${viewport.name}`, "no bottom bar at lg and up", false, await bar.isVisible());
      check(`nav/${viewport.name}`, "rail is in flow", true, await rail.isVisible());
    } else {
      check(`nav/${viewport.name}`, "bottom bar is offered", true, await bar.isVisible());
      check(`nav/${viewport.name}`, "no rail below lg", false, await rail.isVisible());
      await bar.getByRole("button", { name: "More" }).click();
      await page.waitForTimeout(350);
      check(`nav/${viewport.name}`, "More opens its panel", true, await more.isVisible());
      await page.keyboard.press("Escape");
      await page.waitForTimeout(350);
      check(`nav/${viewport.name}`, "Escape closes it", false, await more.isVisible());
    }
    await context.close();
  }
  // A rail with a z-index of its own floats above an open drawer's scrim.
  console.log("\n── drawer scrim ──");
  {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await stubApi(context);
    const page = await context.newPage();
    await page.goto(`${WEB}/`, { waitUntil: "networkidle" });
    await page.waitForTimeout(350);
    // The list is grouped by business unit, so the rows under the header open with a group band; a
    // position row is the one carrying its "Lead ·" line. Clicked at its title, clear of the Open button.
    await page.locator('[role="table"] [role="row"]').filter({ hasText: "Lead ·" }).first()
      .click({ position: { x: 60, y: 12 } });
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
