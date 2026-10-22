import { createServer, type Server } from "node:http";
import puppeteer, { type Browser, type Page } from "puppeteer-core";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { installAudioRecorder } from "../capture/audioRecorderScript.js";
import { audibleSecondsOf } from "./loudness.js";
import { CANARY_CLIP_PATH } from "./phrase.js";
import { installSpeakerScript, SPEAKING_ENERGY, speakerArguments } from "./speaker.js";

/**
 * The canary's ears (#978) in a real Chromium (`CHROME_PATH`): the speaker's clip must reach the bot's recorder
 * through a call's peer connection as audible pieces, and a muted or absent voice must read as silence.
 */
const executablePath = process.env.CHROME_PATH as string;
let server: Server;
let origin: string;

beforeAll(async () => {
  if (!executablePath) {
    throw new Error("CHROME_PATH must name a Chromium for the browser tests");
  }
  // getUserMedia needs a secure context, which a page on the loopback address is.
  server = createServer((_request, response) => response.end("<!doctype html><title>call</title>"));
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  origin = `http://127.0.0.1:${(server.address() as { port: number }).port}`;
});

afterAll(async () => {
  await new Promise((resolve) => server?.close(resolve));
});

async function recordingPage(browser: Browser, pieces: Uint8Array[]): Promise<Page> {
  const page = await browser.newPage();
  await page.exposeFunction("__uncavaAudioPiece", (encoded: string) => {
    pieces.push(new Uint8Array(Buffer.from(encoded, "base64")));
  });
  await page.evaluateOnNewDocument(`(${installAudioRecorder.toString()})()`);
  await page.goto(origin);
  return page;
}

/** A loopback call: one connection sends `source`, the other receives it as a remote track, as the service's would. */
async function call(page: Page, source: "microphone" | "muted-microphone" | "nothing"): Promise<void> {
  await page.evaluate(async (from: string) => {
    const sender = new RTCPeerConnection();
    const receiver = new RTCPeerConnection();
    sender.onicecandidate = (event) => event.candidate && receiver.addIceCandidate(event.candidate);
    receiver.onicecandidate = (event) => event.candidate && sender.addIceCandidate(event.candidate);
    if (from !== "nothing") {
      const microphone = await navigator.mediaDevices.getUserMedia({ audio: true });
      microphone.getAudioTracks().forEach((track) => {
        track.enabled = from === "microphone";
        sender.addTrack(track, microphone);
      });
    } else {
      sender.addTransceiver("audio");
    }
    await sender.setLocalDescription(await sender.createOffer());
    await receiver.setRemoteDescription(sender.localDescription as RTCSessionDescriptionInit);
    await receiver.setLocalDescription(await receiver.createAnswer());
    await sender.setRemoteDescription(receiver.localDescription as RTCSessionDescriptionInit);
    (window as unknown as Record<string, unknown>).__keep = [sender, receiver];
  }, source);
}

async function record(page: Page, pieceMs: number, pieces: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 1_500));
  await page.evaluate(`window.__uncavaStartRecording(${pieceMs})`);
  await new Promise((resolve) => setTimeout(resolve, pieceMs * pieces + 200));
  await page.evaluate("window.__uncavaStopRecording()");
}

describe("the canary's ears in a real browser", () => {
  it("records the speaker's clip, played by the fake microphone across a call, as audible pieces", async () => {
    const speaker = await puppeteer.launch({ executablePath, headless: true, args: speakerArguments(CANARY_CLIP_PATH) });
    try {
      const pieces: Uint8Array[] = [];
      const page = await recordingPage(speaker, pieces);
      await call(page, "microphone");
      await record(page, 5_000, 2);

      // Two whole pieces, and the sliver the stop cuts off the third.
      const audible = (await audibleSecondsOf(executablePath, pieces)).map((seconds) => seconds ?? 0);
      expect(audible.sort((first, second) => second - first).slice(0, 2).every((seconds) => seconds >= 2)).toBe(true);
    } finally {
      await speaker.close();
    }
  }, 60_000);

  it("counts the sound the speaker's microphone sends into a call, and stops counting once it is muted", async () => {
    const speaker = await puppeteer.launch({ executablePath, headless: true, args: speakerArguments(CANARY_CLIP_PATH) });
    try {
      const page = await speaker.newPage();
      await page.evaluateOnNewDocument(`(${installSpeakerScript.toString()})()`);
      await page.goto(origin);
      await call(page, "microphone");
      const energy = () => page.evaluate("window.__canarySpeakerEnergy()") as Promise<number>;
      const growth = async () => {
        const before = await energy();
        await new Promise((resolve) => setTimeout(resolve, 3_000));
        return (await energy()) - before;
      };

      expect(await page.evaluate(() => navigator.mediaDevices.getUserMedia({ audio: true })
        .then((stream) => stream.getAudioTracks()[0].getSettings().echoCancellation))).toBe(false);
      expect(await growth()).toBeGreaterThanOrEqual(SPEAKING_ENERGY);
      await page.evaluate(() => {
        const [sender] = (window as unknown as { __keep: RTCPeerConnection[] }).__keep;
        sender.getSenders().forEach((each) => each.track && (each.track.enabled = false));
      });
      await new Promise((resolve) => setTimeout(resolve, 500));
      expect(await growth()).toBeLessThan(SPEAKING_ENERGY);
    } finally {
      await speaker.close();
    }
  }, 60_000);

  it("reads a muted speaker, and a call nobody speaks in, as silent pieces rather than undecodable ones", async () => {
    const speaker = await puppeteer.launch({ executablePath, headless: true, args: speakerArguments(CANARY_CLIP_PATH) });
    try {
      const muted: Uint8Array[] = [];
      const mutedPage = await recordingPage(speaker, muted);
      await call(mutedPage, "muted-microphone");
      await record(mutedPage, 3_000, 1);

      const nobody: Uint8Array[] = [];
      const quietPage = await recordingPage(speaker, nobody);
      await call(quietPage, "nothing");
      await record(quietPage, 3_000, 1);

      expect(muted.length).toBeGreaterThan(0);
      expect(nobody.length).toBeGreaterThan(0);
      for (const seconds of await audibleSecondsOf(executablePath, [...muted, ...nobody])) {
        expect(seconds).toBe(0);
      }
    } finally {
      await speaker.close();
    }
  }, 60_000);
});
