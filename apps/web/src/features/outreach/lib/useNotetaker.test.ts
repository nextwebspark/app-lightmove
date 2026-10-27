import { describe, expect, it } from "vitest";
import type { NotetakerRecording } from "../api/notetakerApi";
import { liveCopilotOf } from "./useNotetaker";

const recording = (overrides: Partial<NotetakerRecording> = {}): NotetakerRecording => ({
  id: "r1",
  meetingId: "m1",
  startsAt: "2026-10-25T10:00:00Z",
  title: "Intro call",
  status: "RECORDING",
  failureReason: null,
  hasTranscript: false,
  retryable: false,
  noteId: null,
  suggestionsAsked: false,
  pendingSuggestions: 0,
  appliedSuggestions: 0,
  foundSuggestions: 0,
  suggesting: false,
  liveCopilot: true,
  liveStatus: "LISTENING",
  mayChangeCopilot: false,
  joinAt: null,
  recordingStartedAt: "2026-10-25T10:01:00Z",
  endRequested: false,
  ...overrides,
});

describe("liveCopilotOf", () => {
  it("finds the call whose copilot is joining or listening", () => {
    expect(liveCopilotOf([recording({ status: "DONE" }), recording({ id: "r2" })])?.id).toBe("r2");
    expect(liveCopilotOf([recording({ status: "JOINING", liveStatus: null })])?.id).toBe("r1");
  });

  it("finds none where the copilot was never chosen, was stopped or dropped, or the call is over", () => {
    expect(liveCopilotOf([recording({ liveCopilot: false, liveStatus: null })])).toBeNull();
    expect(liveCopilotOf([recording({ liveStatus: "STOPPED" })])).toBeNull();
    expect(liveCopilotOf([recording({ liveStatus: "FEED_STOPPED" })])).toBeNull();
    expect(liveCopilotOf([recording({ status: "PROCESSING" })])).toBeNull();
    expect(liveCopilotOf([recording({ status: "SCHEDULED" })])).toBeNull();
  });
});
