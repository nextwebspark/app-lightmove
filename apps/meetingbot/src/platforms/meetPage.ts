import type { Browser, Page } from "puppeteer-core";
import { delay, openCallTab } from "./callBrowser.js";
import { inPage } from "./inPage.js";
import { isAllowedNavigation, isMeetLink } from "./meetLink.js";
import { fillMeetName, pressMeetButton, readMeetCaptions, readMeetDevices, readMeetScreen,
  writeMeetChat } from "./meetScreen.js";
import type { MeetingPage } from "./platform.js";

export class MeetLinkRefused extends Error {}

/** Google Meet in headless Chromium, driven through Puppeteer. */
export async function openMeet(joinUrl: string, onAudioPiece: (piece: Uint8Array, startedAt: Date) => void,
                               executablePath: string): Promise<MeetingPage> {
  if (!isMeetLink(joinUrl)) {
    throw new MeetLinkRefused("the join link is not a Google Meet link");
  }
  const { browser, page } = await openCallTab(joinUrl, ["https://meet.google.com"], isAllowedNavigation, onAudioPiece,
    executablePath);
  return meetPage(browser, page);
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

