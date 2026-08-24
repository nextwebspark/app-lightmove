import { chromium } from "playwright";
import { payloadFor } from "/home/user/app-lightmove/e2e/spa/responsive-fixtures.mjs";
const b = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
const c = await b.newContext({ viewport: { width: 390, height: 844 } });
await c.route("**/api/v1/**", async (route) => {
  const { pathname } = new URL(route.request().url());
  const body = JSON.stringify(payloadFor(pathname));
  console.log("API", pathname, "->", body.slice(0, 90));
  await route.fulfill({ status: 200, contentType: "application/json", body });
});
const p = await c.newPage();
p.on("console", (m) => console.log("CONSOLE", m.type(), m.text().slice(0, 300)));
p.on("pageerror", (e) => console.log("PAGEERROR", String(e).slice(0, 500)));
await p.goto("http://localhost:5173/clients", { waitUntil: "networkidle" });
await p.waitForTimeout(1500);
console.log("URL:", p.url());
console.log("BODY:", (await p.locator("body").innerText()).slice(0, 400));
console.log("ROOT HTML:", (await p.locator("#root").innerHTML()).slice(0, 400));
await b.close();
