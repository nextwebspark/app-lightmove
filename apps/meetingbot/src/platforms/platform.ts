import type { CaptionEntry, MeetScreen } from "./meetScreen.js";

/** What the runner asks of a call's page, whatever service it is. Teams answers the same questions (#954). */
export interface MeetingPage {
  /**
   * Asks to join as `botName` with microphone and camera off: `blocked` when the service will not let a guest in,
   * `devices-on` when either could not be turned off — the bot never joins a call able to make a sound or a picture.
   */
  requestToJoin(botName: string): Promise<"requested" | "blocked" | "devices-on">;
  screen(): Promise<MeetScreen>;
  /** Writes the consent line in the call's chat; answers whether it was sent. */
  announce(message: string): Promise<boolean>;
  turnOnCaptions(): Promise<void>;
  captions(): Promise<CaptionEntry[]>;
  startAudio(pieceMs: number): Promise<void>;
  /** Stops the recorder and waits until its last piece has been handed over. */
  stopAudio(): Promise<void>;
  leave(): Promise<void>;
  close(): Promise<void>;
}
