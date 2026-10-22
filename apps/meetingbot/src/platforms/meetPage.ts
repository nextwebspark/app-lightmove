import puppeteer, { type Browser, type Page } from "puppeteer-core";
import { installAudioRecorder } from "../capture/audioRecorderScript.js";
import { inPage } from "./inPage.js";
import { BLOCKED_HOSTS, isAllowedNavigation, isMeetLink } from "./meetLink.js";
import { fillMeetName, pressMeetButton, readMeetCaptions, readMeetDevices, readMeetScreen,
  writeMeetChat } from "./meetScreen.js";
import type { MeetingPage } from "./platform.js";

const CHROME_ARGUMENTS = [
  // English labels: the screen readers match on what the page says.
  "--lang=en-US",
  // No fake camera or microphone: Chromium's fake ones are a test card and a beep, and Meet would put both in the
  // executive's call. The page is denied both, and the bot joins as a guest with neither.
  "--autoplay-policy=no-user-gesture-required",
  "--disable-blink-features=AutomationControlled",
  "--no-first-run",
  "--no-default-browser-check",
  "--window-size=1280,800",
  // The container runs one call in one throwaway browser, as an unprivileged user.
  "--no-sandbox",
  "--disable-dev-shm-usage",
];

export class MeetLinkRefused extends Error {}

/** Google Meet in headless Chromium, driven through Puppeteer. */
export async function openMeet(joinUrl: string, onAudioPiece: (piece: Uint8Array) => void,
                               executablePath: string): Promise<MeetingPage> {
  if (!isMeetLink(joinUrl)) {
    throw new MeetLinkRefused("the join link is not a Google Meet link");
  }
  const browser: Browser = await puppeteer.launch({
    executablePath,
    headless: true,
    args: CHROME_ARGUMENTS,
    defaultViewport: { width: 1280, height: 800 },
  });
  try {
    await browser.defaultBrowserContext().overridePermissions("https://meet.google.com", []);
    const page = await browser.newPage();
    await keepToMeet(page);
    // Meet refuses a browser that calls itself HeadlessChrome.
    const agent = (await browser.userAgent()).replace("HeadlessChrome", "Chrome");
    await page.setUserAgent(agent);
    await page.setExtraHTTPHeaders({ "Accept-Language": "en-US,en;q=0.9" });
    await page.exposeFunction("__uncavaAudioPiece", (encoded: string) => {
      onAudioPiece(new Uint8Array(Buffer.from(encoded, "base64")));
    });
    await page.evaluateOnNewDocument(`(${installAudioRecorder.toString()})()`);
    // Never "networkidle": Meet keeps connections open for as long as the page lives.
    await page.goto(joinUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });
    return meetPage(browser, page);
  } catch (error) {
    await browser.close().catch(() => undefined);
    throw error;
  }
}

/** The page reaches no metadata server, and the tab is never taken off Meet and Google's sign-in. */
async function keepToMeet(page: Page): Promise<void> {
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
    const leavesMeet = request.isNavigationRequest() && request.frame() === page.mainFrame()
      && !isAllowedNavigation(request.url());
    if (BLOCKED_HOSTS.has(host) || leavesMeet) {
      void request.abort("blockedbyclient");
      return;
    }
    void request.continue();
  });
}

function meetPage(browser: Browser, page: Page): MeetingPage {
  return {
    async requestToJoin(botName) {
      // The pre-join screen draws its controls a moment after the page settles.
      for (let attempt = 0; attempt < 30; attempt++) {
        const screen = await inPage(page, readMeetScreen);
        if (screen.stage === "blocked") {
          return "blocked";
        }
        if (screen.stage === "prejoin") {
          break;
        }
        await delay(1_000);
      }
      // Denied both devices, Meet asks once whether to go on without them.
      await inPage(page, pressMeetButton, ["continue without microphone and camera"]);
      for (let attempt = 0; attempt < 3; attempt++) {
        const devices = await inPage(page, readMeetDevices);
        if (!devices.microphoneOn && !devices.cameraOn) {
          break;
        }
        if (devices.microphoneOn) {
          await inPage(page, pressMeetButton, ["turn off microphone"]);
        }
        if (devices.cameraOn) {
          await inPage(page, pressMeetButton, ["turn off camera"]);
        }
        await delay(500);
      }
      const devices = await inPage(page, readMeetDevices);
      if (devices.microphoneOn || devices.cameraOn) {
        return "devices-on";
      }
      await inPage(page, fillMeetName, botName);
      await delay(500);
      const asked = await inPage(page, pressMeetButton, ["ask to join", "join now"]);
      if (!asked) {
        return (await inPage(page, readMeetScreen)).stage === "blocked" ? "blocked" : "requested";
      }
      return "requested";
    },
    screen: () => inPage(page, readMeetScreen),
    async announce(message) {
      await inPage(page, pressMeetButton, ["chat with everyone", "open chat", "chat"]);
      await delay(1_000);
      const written = await inPage(page, writeMeetChat, message);
      if (written) {
        await page.keyboard.press("Enter");
      }
      await delay(500);
      await inPage(page, pressMeetButton, ["close"]);
      return written;
    },
    async turnOnCaptions() {
      if (!(await inPage(page, pressMeetButton, ["turn on captions"]))) {
        await page.keyboard.press("c");
      }
    },
    captions: () => inPage(page, readMeetCaptions),
    async startAudio(pieceMs) {
      await page.evaluate(`window.__uncavaStartRecording(${Number(pieceMs)})`);
    },
    async stopAudio() {
      await page.evaluate("window.__uncavaStopRecording && window.__uncavaStopRecording()");
    },
    async leave() {
      await inPage(page, pressMeetButton, ["leave call"]).catch(() => false);
    },
    async close() {
      await browser.close();
    },
  };
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
