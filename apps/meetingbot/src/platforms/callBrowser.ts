import puppeteer, { type Browser, type Page } from "puppeteer-core";
import { installAudioRecorder } from "../capture/audioRecorderScript.js";
import { BLOCKED_HOSTS } from "./meetLink.js";

const CHROME_ARGUMENTS = [
  // English labels: the screen readers match on what the page says.
  "--lang=en-US",
  // No fake camera or microphone: Chromium's fake ones are a test card and a beep, and the service would put both in
  // the executive's call. The page is denied both, and the bot joins as a guest with neither.
  "--autoplay-policy=no-user-gesture-required",
  "--disable-blink-features=AutomationControlled",
  "--no-first-run",
  "--no-default-browser-check",
  "--window-size=1280,800",
  // The container runs one call in one throwaway browser, as an unprivileged user.
  "--no-sandbox",
  "--disable-dev-shm-usage",
];

export interface CallTab {
  browser: Browser;
  page: Page;
}

/**
 * One throwaway Chromium on one call's link, whatever service it is: denied the camera and microphone on the
 * service's origins, held to the hosts `allowedNavigation` names, out of reach of the metadata server, and recording
 * the call's remote audio from before the service's own code runs.
 */
export async function openCallTab(joinUrl: string, deniedOrigins: string[], allowedNavigation: (url: string) => boolean,
                                  onAudioPiece: (piece: Uint8Array, startedAt: Date) => void,
                                  executablePath: string): Promise<CallTab> {
  const browser: Browser = await puppeteer.launch({
    executablePath,
    headless: true,
    args: CHROME_ARGUMENTS,
    defaultViewport: { width: 1280, height: 800 },
  });
  try {
    for (const origin of deniedOrigins) {
      await browser.defaultBrowserContext().overridePermissions(origin, []);
    }
    const page = await browser.newPage();
    await keepTo(page, allowedNavigation);
    // Both services refuse a browser that calls itself HeadlessChrome.
    const agent = (await browser.userAgent()).replace("HeadlessChrome", "Chrome");
    await page.setUserAgent(agent);
    await page.setExtraHTTPHeaders({ "Accept-Language": "en-US,en;q=0.9" });
    await page.exposeFunction("__uncavaAudioPiece", (encoded: string, startedAtMs: number) => {
      onAudioPiece(new Uint8Array(Buffer.from(encoded, "base64")), new Date(startedAtMs));
    });
    await page.evaluateOnNewDocument(`(${installAudioRecorder.toString()})()`);
    // Never "networkidle": a call's page keeps connections open for as long as it lives.
    await page.goto(joinUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
    return { browser, page };
  } catch (error) {
    await browser.close().catch(() => undefined);
    throw error;
  }
}

/** The page reaches no metadata server, and the tab is never taken off the service's own hosts. */
async function keepTo(page: Page, allowedNavigation: (url: string) => boolean): Promise<void> {
  await page.setRequestInterception(true);
  page.on("request", (request) => {
    if (request.isInterceptResolutionHandled()) {
      return;
    }
    let host = "";
    try {
      host = new URL(request.url()).hostname;
    } catch {
      // A data: or blob: address has no host to check.
    }
    const leaves = request.isNavigationRequest() && request.frame() === page.mainFrame()
      && !allowedNavigation(request.url());
    if (BLOCKED_HOSTS.has(host) || leaves) {
      void request.abort("blockedbyclient");
      return;
    }
    void request.continue();
  });
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
