import puppeteer, { type Browser, type Page } from "puppeteer-core";
import type { BotConfig } from "../config.js";
import { CHROME_ARGUMENTS, delay, keepTo } from "../platforms/callBrowser.js";
import { inPage } from "../platforms/inPage.js";
import { isAllowedNavigation, isMeetLink } from "../platforms/meetLink.js";
import { fillMeetName, pressMeetButton, readMeetDevices, readMeetScreen } from "../platforms/meetScreen.js";
import { isAllowedTeamsNavigation, isTeamsLink } from "../platforms/teamsLink.js";
import { fillTeamsName, pressTeamsButton, readTeamsDevices, readTeamsScreen } from "../platforms/teamsScreen.js";
import { pressSpeakerDevice } from "./speakerScreen.js";

export const SPEAKER_NAME = "Uncava Canary Speaker";
/** WebRTC's total audio energy over three seconds of sound at about -45 dBFS; a muted microphone sends ~1e-7. */
export const SPEAKING_ENERGY = 1e-4;

export class SpeakerNotJoined extends Error {}

export interface CanarySpeaker {
  /** Still in the call, and its microphone still sending sound into it. */
  stillSpeaking(): Promise<boolean>;
  leave(): Promise<void>;
}

/** Chromium's fake microphone plays `clipPath` on a loop; the camera is the fake test card, switched off before joining. */
export function speakerArguments(clipPath: string): string[] {
  return [...CHROME_ARGUMENTS, "--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream",
    `--use-file-for-fake-audio-capture=${clipPath}`];
}

interface SpeakerPlatform {
  isLink(joinUrl: string): boolean;
  origins: string[];
  allowedNavigation(url: string): boolean;
  stage(page: Page): Promise<string>;
  microphoneOn(page: Page): Promise<boolean>;
  prepare(page: Page): Promise<void>;
  join(page: Page, name: string): Promise<boolean>;
  leave(page: Page): Promise<boolean>;
}

const MEET: SpeakerPlatform = {
  isLink: isMeetLink,
  origins: ["https://meet.google.com"],
  allowedNavigation: isAllowedNavigation,
  stage: async (page) => (await inPage(page, readMeetScreen)).stage,
  microphoneOn: async (page) => (await inPage(page, readMeetDevices)).microphoneOn,
  prepare: async () => undefined,
  async join(page, name) {
    await inPage(page, fillMeetName, name);
    await delay(500);
    return inPage(page, pressMeetButton, ["join now", "ask to join"]);
  },
  leave: (page) => inPage(page, pressMeetButton, ["leave call"]),
};

const TEAMS: SpeakerPlatform = {
  isLink: isTeamsLink,
  origins: ["https://teams.microsoft.com", "https://teams.live.com", "https://teams.cloud.microsoft"],
  allowedNavigation: isAllowedTeamsNavigation,
  stage: async (page) => (await inPage(page, readTeamsScreen)).stage,
  microphoneOn: async (page) => (await inPage(page, readTeamsDevices)).microphoneOn,
  async prepare(page) {
    await inPage(page, pressTeamsButton, ["continue on this browser", "join on the web instead"]);
  },
  async join(page, name) {
    await inPage(page, fillTeamsName, name);
    await delay(500);
    return inPage(page, pressTeamsButton, ["prejoin-join-button", "join now"]);
  },
  leave: (page) => inPage(page, pressTeamsButton, ["hangup", "leave"]),
};

/**
 * The canary's second guest (#978): joins the standing test meeting before the bot, microphone on, and says the
 * canary phrase on a loop until it is told to leave, so the bot has a voice to record.
 */
export async function joinAsSpeaker(platform: BotConfig["platform"], joinUrl: string, clipPath: string,
                                    executablePath: string): Promise<CanarySpeaker> {
  const service = platform === "MICROSOFT_TEAMS" ? TEAMS : MEET;
  if (!service.isLink(joinUrl)) {
    throw new SpeakerNotJoined(`the canary link is not a ${platform} link`);
  }
  const browser: Browser = await puppeteer.launch({
    executablePath,
    headless: true,
    args: speakerArguments(clipPath),
    defaultViewport: { width: 1280, height: 800 },
  });
  try {
    const page = await browser.newPage();
    await keepTo(page, service.allowedNavigation);
    await page.setUserAgent((await browser.userAgent()).replace("HeadlessChrome", "Chrome"));
    await page.setExtraHTTPHeaders({ "Accept-Language": "en-US,en;q=0.9" });
    await page.evaluateOnNewDocument(`(${installSpeakerScript.toString()})()`);
    await page.goto(joinUrl, { waitUntil: "domcontentloaded", timeout: 60_000 });

    if (!(await waitForStage(service, page, "prejoin", 45))) {
      throw new SpeakerNotJoined("no pre-join screen");
    }
    if (!(await speakWithoutCamera(service, page))) {
      throw new SpeakerNotJoined("the microphone would not turn on before joining");
    }
    if (!(await service.join(page, SPEAKER_NAME))) {
      throw new SpeakerNotJoined("no join button");
    }
    if (!(await waitForStage(service, page, "in-call", 60))) {
      throw new SpeakerNotJoined("not let into the call");
    }
    if (!(await speakWithoutCamera(service, page))) {
      throw new SpeakerNotJoined("muted in the call");
    }
    return {
      async stillSpeaking() {
        if ((await service.stage(page)) !== "in-call") {
          return false;
        }
        const before = await outboundEnergy(page);
        await delay(3_000);
        return (await outboundEnergy(page)) - before >= SPEAKING_ENERGY;
      },
      async leave() {
        await service.leave(page).catch(() => false);
        await browser.close();
      },
    };
  } catch (error) {
    await browser.close().catch(() => undefined);
    throw error;
  }
}

async function waitForStage(service: SpeakerPlatform, page: Page, wanted: string, seconds: number): Promise<boolean> {
  for (let second = 0; second < seconds; second++) {
    const stage = await service.stage(page);
    if (stage === wanted) {
      return true;
    }
    if (stage === "blocked" || stage === "denied" || stage === "ended") {
      return false;
    }
    if (stage === "launcher") {
      await service.prepare(page);
    }
    await delay(1_000);
  }
  return false;
}

/** One control a pass, the next only once the page has redrawn: pressing a switch mid-redraw turns it back. */
async function speakWithoutCamera(service: SpeakerPlatform, page: Page): Promise<boolean> {
  for (let pass = 0; pass < 4; pass++) {
    if ((await inPage(page, pressSpeakerDevice)) === "none") {
      break;
    }
    await delay(1_000);
  }
  return service.microphoneOn(page);
}

function outboundEnergy(page: Page): Promise<number> {
  return page.evaluate("window.__canarySpeakerEnergy()") as Promise<number>;
}

/**
 * Installed in the speaker's page before the service's code runs: the microphone is asked for without echo
 * cancellation, noise suppression or gain control, which Chromium warns distort a file played as the microphone, and
 * every peer connection is kept so `__canarySpeakerEnergy` can sum the sound the microphone has sent into the call.
 * Self-contained: it is sent to the page as its source text.
 */
export function installSpeakerScript(): void {
  const page = window as unknown as Record<string, unknown> & Window;
  const connections: RTCPeerConnection[] = [];
  const Original = window.RTCPeerConnection;
  const Wrapped = function (this: RTCPeerConnection, ...args: ConstructorParameters<typeof RTCPeerConnection>) {
    const connection = new Original(...args);
    connections.push(connection);
    return connection;
  } as unknown as typeof RTCPeerConnection;
  Wrapped.prototype = Original.prototype;
  Object.setPrototypeOf(Wrapped, Original);
  window.RTCPeerConnection = Wrapped;

  const devices = navigator.mediaDevices;
  if (devices) {
    const getUserMedia = devices.getUserMedia.bind(devices);
    devices.getUserMedia = (constraints?: MediaStreamConstraints) => {
      const audio = constraints?.audio;
      if (!audio) {
        return getUserMedia(constraints);
      }
      return getUserMedia({ ...constraints, audio: { ...(typeof audio === "object" ? audio : {}),
        echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
    };
  }

  page.__canarySpeakerEnergy = async () => {
    let energy = 0;
    for (const connection of connections) {
      if (connection.connectionState === "closed") {
        continue;
      }
      (await connection.getStats()).forEach((report: { type: string; kind?: string; totalAudioEnergy?: number }) => {
        if (report.type === "media-source" && report.kind === "audio") {
          energy += report.totalAudioEnergy ?? 0;
        }
      });
    }
    return energy;
  };
}
