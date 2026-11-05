import { describe, expect, it } from "vitest";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerRecording } from "../api/notetakerApi";
import {
  callOfRecording,
  canRecord,
  failureWordsOf,
  hasCallEnded,
  hasLiveRecording,
  hasRecordingAboutToMove,
  isNotetakerLive,
  isRecordingCall,
  pairCallRecordings,
} from "./notetakerChip";

const recording = (overrides: Partial<NotetakerRecording> = {}): NotetakerRecording => ({
  id: "r1",
  meetingId: "m1",
  startsAt: "2026-10-07T06:00:00Z",
  endsAt: "2026-10-07T06:30:00Z",
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
    const shownOn = (bot: NotetakerRecording) => pairCallRecordings([meeting], [bot]).ofMeeting.get("m9")?.id;
    expect(shownOn(recording({ meetingId: "gone" }))).toBe("r1");
    expect(shownOn(recording({ meetingId: "gone", startsAt: "2026-10-08T06:00:00Z" }))).toBeUndefined();
    expect(shownOn(recording({ meetingId: "gone", title: "Another call" }))).toBeUndefined();
  });

  it("leaves a bot sent before its call moved off the call, whose row it still names", () => {
    const moved = { id: "m1", startsAt: "2026-10-07T15:40:00.000Z", title: "First conversation" } as Meeting;
    const shownOn = (bot: NotetakerRecording) => pairCallRecordings([moved], [bot]).ofMeeting.get("m1")?.id;
    expect(shownOn(recording({ meetingId: "m1", startsAt: "2026-10-08T05:40:00Z" }))).toBeUndefined();
    expect(shownOn(recording({ meetingId: "m1", startsAt: "2026-10-07T15:40:00Z" }))).toBe("r1");
  });

  it("gives a finished bot whose call left the calendar a row of its own, drawn from what it was sent for", () => {
    const replaced = { id: "m2", startsAt: "2026-10-07T06:00:00.000Z", title: "New Teams link" } as Meeting;
    const done = recording({ status: "DONE", hasTranscript: true, pendingSuggestions: 4 });

    const paired = pairCallRecordings([replaced], [done]);

    expect(paired.ofMeeting.size).toBe(0);
    expect(paired.unpaired.map((bot) => bot.id)).toEqual(["r1"]);
    const call = callOfRecording(done);
    expect(call).toMatchObject({
      title: "First conversation",
      startsAt: done.startsAt,
      endsAt: done.endsAt,
      joinUrl: null,
      editable: false,
    });
    expect(isRecordingCall(call)).toBe(true);
    expect(isRecordingCall(replaced)).toBe(false);
  });

  it("keeps a waiting, called-back or spent bot that lost its call off the list", () => {
    const unpaired = pairCallRecordings(
      [],
      [
        recording({ id: "waiting" }),
        recording({ id: "called-back", status: "CANCELLED" }),
        recording({ id: "spent", status: "FAILED" }),
        recording({ id: "retryable", status: "FAILED", retryable: true }),
        recording({ id: "in-the-call", status: "RECORDING" }),
      ],
    ).unpaired;

    expect(unpaired.map((bot) => bot.id)).toEqual(["retryable", "in-the-call"]);
  });

  it("shows a call's finished bot though a later attempt was called back, a second recording on its own row", () => {
    const meeting = { id: "m1", startsAt: "2026-10-07T06:00:00.000Z", title: "First conversation" } as Meeting;
    const done = recording({ id: "done", status: "DONE" });

    const behindACancel = pairCallRecordings([meeting], [recording({ id: "later", status: "CANCELLED" }), done]);
    expect(behindACancel.ofMeeting.get("m1")?.id).toBe("done");
    expect(behindACancel.unpaired).toEqual([]);

    const twice = pairCallRecordings([meeting], [recording({ id: "second", status: "DONE" }), done]);
    expect(twice.ofMeeting.get("m1")?.id).toBe("second");
    expect(twice.unpaired.map((bot) => bot.id)).toEqual(["done"]);
  });

  it("draws a bot on one row only, its own row before another sharing its start and title", () => {
    const copy = { id: "copy", startsAt: "2026-10-07T06:00:00.000Z", title: "First conversation" } as Meeting;
    const own = { id: "m1", startsAt: "2026-10-07T06:00:00.000Z", title: "First conversation" } as Meeting;

    const paired = pairCallRecordings([copy, own], [recording({ status: "DONE" })]);

    expect(paired.ofMeeting.get("m1")?.id).toBe("r1");
    expect(paired.ofMeeting.has("copy")).toBe(false);
    expect(paired.unpaired).toEqual([]);
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
