import type { Browser, Page } from "puppeteer-core";
import { delay, openCallTab } from "./callBrowser.js";
import { inPage } from "./inPage.js";
import type { MeetingPage } from "./platform.js";
import { isAllowedTeamsNavigation, isTeamsLink } from "./teamsLink.js";
import { fillTeamsName, focusTeamsChat, pressTeamsButton, readTeamsCaptions, readTeamsDevices, readTeamsScreen,
  teamsChatShows, turnOffTeamsDevices } from "./teamsScreen.js";

export class TeamsLinkRefused extends Error {}

/** Thrown, never fatal: a call without Teams' captions is still recorded, its voices left unnamed. */
export class CaptionsUnavailable extends Error {
  constructor() {
    super("CAPTIONS_UNAVAILABLE: Teams offered no live captions to turn on");
  }
}

/** How long the launcher and the pre-join screen get to appear before a guest is taken as not let in at all. */
const PREJOIN_WAIT_SECONDS = 45;

/** Microsoft Teams' web client in headless Chromium, as a guest: never the desktop app, never a sign-in. */
export async function openTeams(joinUrl: string, onAudioPiece: (piece: Uint8Array, startedAt: Date) => void,
                                executablePath: string): Promise<MeetingPage> {
  if (!isTeamsLink(joinUrl)) {
    throw new TeamsLinkRefused("the join link is not a Microsoft Teams link");
  }
  const { browser, page } = await openCallTab(joinUrl,
    ["https://teams.microsoft.com", "https://teams.live.com", "https://teams.cloud.microsoft"], isAllowedTeamsNavigation,
    onAudioPiece, executablePath);
  return teamsPage(browser, page);
}

function teamsPage(browser: Browser, page: Page): MeetingPage {
  return {
    async requestToJoin(botName) {
      // A link opens Teams' launcher, which offers the app; the bot always stays in this browser. A tenant that lets
      // no guest in either says so or sends the tab to sign in, which it is held back from: no pre-join screen at all.
      let reachedPrejoin = false;
      for (let second = 0; second < PREJOIN_WAIT_SECONDS && !reachedPrejoin; second++) {
        const { stage } = await inPage(page, readTeamsScreen);
        if (stage === "blocked") {
          return "blocked";
        }
        if (stage === "launcher") {
          await inPage(page, pressTeamsButton, ["continue on this browser", "join on the web instead"]);
        }
        reachedPrejoin = stage === "prejoin";
        if (!reachedPrejoin) {
          await delay(1_000);
        }
      }
      if (!reachedPrejoin) {
        return "blocked";
      }
      // Denied both devices, Teams asks once whether to go on without them.
      await inPage(page, pressTeamsButton, ["continue without audio or video"]);
      await delay(500);
      for (let attempt = 0; attempt < 3; attempt++) {
        const devices = await inPage(page, readTeamsDevices);
        if (!devices.microphoneOn && !devices.cameraOn) {
          break;
        }
        await inPage(page, turnOffTeamsDevices);
        await delay(500);
      }
      const devices = await inPage(page, readTeamsDevices);
      if (devices.microphoneOn || devices.cameraOn) {
        return "devices-on";
      }
      await inPage(page, fillTeamsName, botName);
      await delay(500);
      if (!(await inPage(page, pressTeamsButton, ["prejoin-join-button", "join now"]))) {
        return (await inPage(page, readTeamsScreen)).stage === "blocked" ? "blocked" : "requested";
      }
      return "requested";
    },
    async screen() {
      const { stage, participants } = await inPage(page, readTeamsScreen);
      return { stage: stage === "launcher" ? "unknown" : stage, participants };
    },
    async announce(message) {
      // Chat is a toggle: pressed while its pane is already open (a retry, say), it would close it.
      if (!(await inPage(page, focusTeamsChat))) {
        await inPage(page, pressTeamsButton, ["show conversation", "open chat", "chat-button", "chat"]);
        await delay(1_500);
        if (!(await inPage(page, focusTeamsChat))) {
          return false;
        }
      }
      await page.keyboard.type(message);
      await page.keyboard.press("Enter");
      // Taken as said only once the chat shows it outside the box it was typed in.
      for (let attempt = 0; attempt < 5; attempt++) {
        await delay(1_000);
        if (await inPage(page, teamsChatShows, message)) {
          return true;
        }
      }
      return false;
    },
    async turnOnCaptions() {
      if (await inPage(page, pressTeamsButton, ["turn on live captions", "show live captions"])) {
        return;
      }
      // Otherwise behind More → Language and speech, a menu at a time.
      if (await inPage(page, pressTeamsButton, ["more actions", "callingbuttons-showmoremenu", "more"])) {
        await delay(800);
        await inPage(page, pressTeamsButton, ["language and speech"]);
        await delay(800);
        if (await inPage(page, pressTeamsButton, ["turn on live captions", "show live captions", "live captions"])) {
          return;
        }
        await page.keyboard.press("Escape");
      }
      throw new CaptionsUnavailable();
    },
    captions: () => inPage(page, readTeamsCaptions),
    async startAudio(pieceMs) {
      await page.evaluate(`window.__uncavaStartRecording(${Number(pieceMs)})`);
    },
    async stopAudio() {
      await page.evaluate("window.__uncavaStopRecording && window.__uncavaStopRecording()");
    },
    async leave() {
      await inPage(page, pressTeamsButton, ["hangup", "leave"]).catch(() => false);
    },
    async close() {
      await browser.close();
    },
  };
}
