import { describe, expect, it } from "vitest";
import type { NotetakerMeeting } from "../api/notetakerApi";
import { joinsLineOf, notetakerChipOf } from "./notetakerChip";

const failed = (overrides: Partial<NotetakerMeeting>): NotetakerMeeting => ({
  meetingId: "m1",
  platform: "GOOGLE_MEET",
  joinable: true,
  enabled: true,
  recordingId: "r1",
  status: "FAILED",
  endReason: "CONSENT_NOT_SHOWN",
  recorder: "OWN",
  joinedAt: null,
  recordedUntil: null,
  transcriptHere: false,
  noteId: null,
  notesElsewhere: false,
  ...overrides,
});

describe("notetakerChipOf", () => {
  it("says a Teams meeting's guest chat is why the bot couldn't say it was recording", () => {
    expect(notetakerChipOf(failed({ platform: "MICROSOFT_TEAMS" }), () => "")?.title).toBe(
      "This meeting doesn't let guests chat, so the notetaker couldn't say it was recording and left without recording.",
    );
  });

  it("keeps the general sentence on Meet", () => {
    expect(notetakerChipOf(failed({}), () => "")?.title).toBe(
      "It couldn't say in the chat that it was recording, so it left without recording.",
    );
  });
});

describe("joinsLineOf", () => {
  it("names every platform the bot joins", () => {
    expect(joinsLineOf(["GOOGLE_MEET", "MICROSOFT_TEAMS"])).toBe(
      "The notetaker joins Google Meet and Microsoft Teams calls.",
    );
  });
});
