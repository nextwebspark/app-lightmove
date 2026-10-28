import { describe, expect, it } from "vitest";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import {
  canRecord,
  failureWordsOf,
  hasCallEnded,
  hasLiveRecording,
  hasRecordingAboutToMove,
  isNotetakerLive,
  recordingOfMeeting,
} from "./notetakerChip";

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
  foundSuggestions: 0,
  suggesting: false,
  liveCopilot: false,
  liveStatus: null,
  mayChangeCopilot: true,
  joinAt: "2026-10-07T05:59:00Z",
  recordingStartedAt: null,
  endRequested: false,
  recordingAvailableUntil: null,
  mayDownloadRecording: false,
  ...overrides,
});

describe("notetakerChip", () => {
  it("takes a call for over once its Notetaker has left it, and for set up while its bot is on its way or in it", () => {
    expect(hasCallEnded(recording({ status: "RECORDING", endRequested: true }))).toBe(true);
    expect(hasCallEnded(recording({ status: "PROCESSING" }))).toBe(true);
    expect(hasCallEnded(recording({ status: "DONE" }))).toBe(true);
    expect(hasCallEnded(recording({ status: "RECORDING" }))).toBe(false);
    expect(hasCallEnded(recording({ status: "FAILED" }))).toBe(false);
    expect(hasCallEnded(null)).toBe(false);
    expect(isNotetakerLive(recording({ status: "SCHEDULED" }))).toBe(true);
    expect(isNotetakerLive(recording({ status: "RECORDING" }))).toBe(true);
    expect(isNotetakerLive(recording({ status: "CANCELLED" }))).toBe(false);
    expect(isNotetakerLive(null)).toBe(false);
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

  it("leaves a bot sent before its call moved off the call, whose row it still names", () => {
    const moved = { id: "m1", startsAt: "2026-10-07T15:40:00.000Z", title: "First conversation" } as Meeting;
    expect(recordingOfMeeting([recording({ meetingId: "m1", startsAt: "2026-10-08T05:40:00Z" })], moved)).toBeNull();
    expect(recordingOfMeeting([recording({ meetingId: "m1", startsAt: "2026-10-07T15:40:00Z" })], moved)?.id).toBe("r1");
  });

  it("records again only once no bot is on its way", () => {
    expect(canRecord(null)).toBe(true);
    expect(canRecord(recording({ status: "FAILED" }))).toBe(true);
    expect(canRecord(recording({ status: "JOINING" }))).toBe(false);
    expect(hasLiveRecording([recording({ status: "DONE" }), recording({ status: "PROCESSING" })])).toBe(true);
    expect(hasLiveRecording([recording({ status: "DONE" })])).toBe(false);
  });

  it("reads sooner while a bot joins, leaves or is written up, never while it waits for the start", () => {
    expect(hasRecordingAboutToMove([recording({ status: "SCHEDULED", joinAt: null })])).toBe(true);
    expect(hasRecordingAboutToMove([recording({ status: "JOINING" })])).toBe(true);
    expect(hasRecordingAboutToMove([recording({ status: "RECORDING", endRequested: true })])).toBe(true);
    expect(hasRecordingAboutToMove([recording({ status: "PROCESSING" })])).toBe(true);
    expect(hasRecordingAboutToMove([recording({ status: "DONE", suggesting: true })])).toBe(true);
    expect(hasRecordingAboutToMove([recording({ status: "SCHEDULED" })])).toBe(false);
    expect(hasRecordingAboutToMove([recording({ status: "RECORDING" })])).toBe(false);
    expect(hasRecordingAboutToMove([recording({ status: "DONE" })])).toBe(false);
  });
});
