import type { BotConfig } from "../config.js";
import { openMeet } from "./meetPage.js";
import type { MeetingPage } from "./platform.js";
import { openTeams } from "./teamsPage.js";

/** The call's page on whichever service the run names; each refuses a link off its own host. */
export function openCall(platform: BotConfig["platform"], joinUrl: string,
                         onAudioPiece: (piece: Uint8Array, startedAt: Date) => void,
                         executablePath: string): Promise<MeetingPage> {
  return platform === "MICROSOFT_TEAMS"
    ? openTeams(joinUrl, onAudioPiece, executablePath)
    : openMeet(joinUrl, onAudioPiece, executablePath);
}
