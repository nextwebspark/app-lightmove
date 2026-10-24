import { describe, expect, it } from "vitest";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import { canRecord, failureWordsOf, hasLiveRecording, notetakerChipOf, recordingOfMeeting } from "./notetakerChip";

const recording = (overrides: Partial<NotetakerRecording> = {}): NotetakerRecording => ({
  id: "r1",
  meetingId: "m1",
  startsAt: "2026-10-07T06:00:00Z",
  title: "First conversation",
  status: "SCHEDULED",
  failureReason: null,
  hasTranscript: false,
  retryable: false,
  noteId: null,
  suggestionsAsked: false,
  pendingSuggestions: 0,
  appliedSuggestions: 0,
  liveCopilot: false,
  liveStatus: null,
  ...overrides,
});

describe("notetakerChip", () => {
  it("offers Remove until the call is over, the transcript once filed, and Try again only where it helps", () => {
    expect(notetakerChipOf(recording({ status: "RECORDING" }))).toEqual({
      label: "Recording",
      tone: "live",
      action: "remove",
    });
    expect(notetakerChipOf(recording({ status: "PROCESSING" }))?.action).toBeNull();
    expect(notetakerChipOf(recording({ status: "DONE", hasTranscript: true }))?.action).toBe("transcript");
    expect(notetakerChipOf(recording({ status: "FAILED", failureReason: "NEVER_RECORDED" }))).toEqual({
      label: "Notetaker failed: it never got into the call",
      tone: "failed",
      action: null,
    });
    expect(notetakerChipOf(recording({ status: "CANCELLED" }))).toBeNull();
  });

  it("words a reason it has never met as something true", () => {
    expect(failureWordsOf("a_new_recall_code")).toBe("something went wrong");
    expect(failureWordsOf(null)).toBe("something went wrong");
  });

  it("finds a call's bot by its row, or by its start and title once a calendar re-read replaced the row", () => {
    const meeting = { id: "m9", startsAt: "2026-10-07T06:00:00.000Z", title: " first CONVERSATION" } as Meeting;
    expect(recordingOfMeeting([recording({ meetingId: "gone" })], meeting)?.id).toBe("r1");
    expect(recordingOfMeeting([recording({ meetingId: "gone", startsAt: "2026-10-08T06:00:00Z" })], meeting)).toBeNull();
    expect(recordingOfMeeting([recording({ meetingId: "gone", title: "Another call" })], meeting)).toBeNull();
  });

  it("records again only once no bot is on its way", () => {
    expect(canRecord(null)).toBe(true);
    expect(canRecord(recording({ status: "FAILED" }))).toBe(true);
    expect(canRecord(recording({ status: "JOINING" }))).toBe(false);
    expect(hasLiveRecording([recording({ status: "DONE" }), recording({ status: "PROCESSING" })])).toBe(true);
    expect(hasLiveRecording([recording({ status: "DONE" })])).toBe(false);
  });
});
