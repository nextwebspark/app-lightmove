import { readFileSync } from "node:fs";
import puppeteer, { type Browser } from "puppeteer-core";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { installAudioRecorder } from "./capture/audioRecorderScript.js";
import { inPage } from "./platforms/inPage.js";
import { readMeetCaptions, readMeetScreen } from "./platforms/meetScreen.js";
import { readTeamsCaptions, readTeamsScreen, teamsChatShows } from "./platforms/teamsScreen.js";
import { consentLine } from "./runner.js";

/**
 * What jsdom cannot run, in a real Chromium: the recorder must turn a peer connection's remote audio into WebM
 * pieces, and the screen readers must survive being sent to a page as source text. `CHROME_PATH` names the browser.
 */
const executablePath = process.env.CHROME_PATH;
let browser: Browser;

beforeAll(async () => {
  if (!executablePath) {
    throw new Error("CHROME_PATH must name a Chromium for the browser tests");
  }
  browser = await puppeteer.launch({
    executablePath,
    headless: true,
    args: ["--no-sandbox", "--autoplay-policy=no-user-gesture-required", "--use-fake-ui-for-media-stream"],
  });
}, 30_000);

afterAll(async () => {
  await browser?.close();
});

describe("in a real browser", () => {
  it("records a call's remote audio as self-contained WebM pieces", async () => {
    const page = await browser.newPage();
    const pieces: Uint8Array[] = [];
    const starts: number[] = [];
    await page.exposeFunction("__uncavaAudioPiece", (encoded: string, startedAtMs: number) => {
      pieces.push(new Uint8Array(Buffer.from(encoded, "base64")));
      starts.push(startedAtMs);
    });
    await page.evaluateOnNewDocument(`(${installAudioRecorder.toString()})()`);
    await page.goto("about:blank");
    // A loopback call: one connection sends a tone, the other receives it as a remote track, as Meet's would.
    await page.evaluate(async () => {
      const tone = new AudioContext();
      const oscillator = tone.createOscillator();
      const out = tone.createMediaStreamDestination();
      oscillator.connect(out);
      oscillator.start();
      const sender = new RTCPeerConnection();
      const receiver = new RTCPeerConnection();
      sender.onicecandidate = (event) => event.candidate && receiver.addIceCandidate(event.candidate);
      receiver.onicecandidate = (event) => event.candidate && sender.addIceCandidate(event.candidate);
      out.stream.getTracks().forEach((track) => sender.addTrack(track, out.stream));
      await sender.setLocalDescription(await sender.createOffer());
      await receiver.setRemoteDescription(sender.localDescription as RTCSessionDescriptionInit);
      await receiver.setLocalDescription(await receiver.createAnswer());
      await sender.setRemoteDescription(receiver.localDescription as RTCSessionDescriptionInit);
      (window as unknown as Record<string, unknown>).__keep = [tone, sender, receiver];
    });
    await new Promise((resolve) => setTimeout(resolve, 1_500));
    await page.evaluate("window.__uncavaStartRecording(1000)");
    await new Promise((resolve) => setTimeout(resolve, 3_200));
    await page.evaluate("window.__uncavaStopRecording()");
    await new Promise((resolve) => setTimeout(resolve, 500));

    expect(pieces.length).toBeGreaterThanOrEqual(3);
    // Each began about one piece's length after the last. Pieces ending together may be handed over in either
    // order, which is why the server stitches on these times rather than on the upload's number.
    const ordered = [...starts].sort((first, second) => first - second);
    for (let index = 1; index < ordered.length; index++) {
      expect(ordered[index] - ordered[index - 1]).toBeGreaterThan(800);
      expect(ordered[index] - ordered[index - 1]).toBeLessThan(1_500);
    }
    for (const piece of pieces) {
      // Every piece opens with the EBML header, so each can be decoded on its own.
      expect(Array.from(piece.subarray(0, 4))).toEqual([0x1a, 0x45, 0xdf, 0xa3]);
      expect(piece.byteLength).toBeGreaterThan(200);
    }
    await page.close();
  }, 30_000);

  it("reads a call's screen and captions when the readers are sent as source text", async () => {
    const page = await browser.newPage();
    await page.setContent(readFileSync(new URL("./platforms/fixtures/meet-in-call.html", import.meta.url), "utf8"));

    expect(await inPage(page, readMeetScreen)).toEqual({ stage: "in-call", participants: 3 });
    expect((await inPage(page, readMeetCaptions)).map((entry) => entry.speaker)).toEqual(["Yara Haddad",
      "Priya Raman"]);
    await page.close();
  }, 30_000);

  it("reads a Teams call the same way, its readers sent as source text", async () => {
    const page = await browser.newPage();
    await page.setContent(readFileSync(new URL("./platforms/fixtures/teams-in-call.html", import.meta.url), "utf8"));

    expect(await inPage(page, readTeamsScreen)).toEqual({ stage: "in-call", participants: 3 });
    expect((await inPage(page, readTeamsCaptions)).map((entry) => entry.speaker)).toEqual(["Priya Raman",
      "Yara Haddad"]);
    expect(await inPage(page, teamsChatShows, consentLine("Uncava Notetaker"))).toBe(true);
    await page.close();
  }, 30_000);
});
