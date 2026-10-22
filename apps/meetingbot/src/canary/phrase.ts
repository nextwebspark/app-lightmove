import { fileURLToPath } from "node:url";

/**
 * What the canary's speaker says, on a loop, as `assets/canary-phrase.wav`: macOS `say -v Samantha -r 165`, then
 * `ffmpeg -af "adelay=300,apad=pad_dur=1.5" -ar 16000 -ac 1 -c:a pcm_s16le`. Change both together.
 */
export const CANARY_PHRASE = "Purple giraffes read quiet library books every morning.";

export const CANARY_CLIP_PATH = fileURLToPath(new URL("../../assets/canary-phrase.wav", import.meta.url));

/** "Quiet" is left out: it is heard as "quite" as often as not. */
const WORDS = ["purple", "giraffe", "library", "book", "morning"];
/** A transcriber may mishear a word of a looped phrase; four of the five is no accident. */
const WORDS_TO_HEAR = 4;

export type Hearing = "phrase" | "other-words" | "nothing";

export function hearingOf(transcript: string): Hearing {
  const heard = transcript.toLowerCase().replace(/[^a-z]+/g, " ").trim();
  if (!heard) {
    return "nothing";
  }
  const words = heard.split(" ");
  const found = WORDS.filter((word) => words.some((said) => said.startsWith(word))).length;
  return found >= WORDS_TO_HEAR ? "phrase" : "other-words";
}
