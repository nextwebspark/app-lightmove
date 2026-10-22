import puppeteer from "puppeteer-core";

const WINDOW_MS = 50;
/** About -45 dBFS: a voice is well above it; a muted track, digital silence and comfort noise are below. */
const AUDIBLE_RMS = 0.0056;

/**
 * How many seconds of each captured WebM piece are louder than silence, decoded to PCM by a throwaway Chromium —
 * the same pieces the transcript is made from. Null for a piece the browser could not decode.
 */
export async function audibleSecondsOf(executablePath: string, pieces: Uint8Array[]): Promise<(number | null)[]> {
  const browser = await puppeteer.launch({ executablePath, headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage"] });
  try {
    const page = await browser.newPage();
    return await page.evaluate(measure, pieces.map((piece) => Buffer.from(piece).toString("base64")), WINDOW_MS,
      AUDIBLE_RMS);
  } finally {
    await browser.close();
  }
}

async function measure(encoded: string[], windowMs: number, audibleRms: number): Promise<(number | null)[]> {
  const context = new OfflineAudioContext(1, 1, 48_000);
  const seconds: (number | null)[] = [];
  for (const piece of encoded) {
    const bytes = Uint8Array.from(atob(piece), (character) => character.charCodeAt(0));
    let audio: AudioBuffer;
    try {
      audio = await context.decodeAudioData(bytes.buffer);
    } catch {
      seconds.push(null);
      continue;
    }
    const channels = Array.from({ length: audio.numberOfChannels }, (_, channel) => audio.getChannelData(channel));
    const size = Math.max(1, Math.round((audio.sampleRate * windowMs) / 1_000));
    let audibleWindows = 0;
    for (let start = 0; start + size <= audio.length; start += size) {
      let energy = 0;
      for (let index = start; index < start + size; index++) {
        let sample = 0;
        for (const channel of channels) {
          sample += channel[index];
        }
        sample /= channels.length;
        energy += sample * sample;
      }
      if (Math.sqrt(energy / size) >= audibleRms) {
        audibleWindows++;
      }
    }
    seconds.push((audibleWindows * windowMs) / 1_000);
  }
  return seconds;
}
