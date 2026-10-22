import type { CaptionEntry, MeetScreen } from "./meetScreen.js";

/** What the runner asks of a call's page, whatever service it is. Teams answers the same questions (#954). */
export interface MeetingPage {
  /** Opens the link and asks to join as `botName`: `blocked` when the service will not let a guest in. */
  requestToJoin(botName: string): Promise<"requested" | "blocked">;
  screen(): Promise<MeetScreen>;
  /** Once in: the consent line in the chat, then captions on. Neither failing stops the recording. */
  announce(message: string): Promise<void>;
  turnOnCaptions(): Promise<void>;
  captions(): Promise<CaptionEntry[]>;
  startAudio(pieceMs: number): Promise<void>;
  /** Stops the recorder and waits until its last piece has been handed over. */
  stopAudio(): Promise<void>;
  leave(): Promise<void>;
  close(): Promise<void>;
}
