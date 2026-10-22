import puppeteer, { type Browser, type Page } from "puppeteer-core";
import { installAudioRecorder } from "../capture/audioRecorderScript.js";
import { inPage } from "./inPage.js";
import { fillMeetName, pressMeetButton, readMeetCaptions, readMeetScreen, writeMeetChat } from "./meetScreen.js";
import type { MeetingPage } from "./platform.js";

const CHROME_ARGUMENTS = [
  // English labels: the screen readers match on what the page says.
  "--lang=en-US",
  // A guest with no camera or microphone, granted without a prompt nobody could answer.
  "--use-fake-ui-for-media-stream",
  "--use-fake-device-for-media-stream",
  "--autoplay-policy=no-user-gesture-required",
  "--disable-blink-features=AutomationControlled",
  "--no-first-run",
  "--no-default-browser-check",
  "--window-size=1280,800",
  // The container runs one call in one throwaway browser, as an unprivileged user.
  "--no-sandbox",
  "--disable-dev-shm-usage",
];

/** Google Meet in headless Chromium, driven through Puppeteer. */
export async function openMeet(joinUrl: string, onAudioPiece: (piece: Uint8Array) => void,
                               executablePath: string): Promise<MeetingPage> {
  const browser: Browser = await puppeteer.launch({
    executablePath,
    headless: true,
    args: CHROME_ARGUMENTS,
    defaultViewport: { width: 1280, height: 800 },
  });
  try {
    const page = await browser.newPage();
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
      if (await inPage(page, writeMeetChat, message)) {
        await page.keyboard.press("Enter");
      }
      await delay(500);
      await inPage(page, pressMeetButton, ["close"]);
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
