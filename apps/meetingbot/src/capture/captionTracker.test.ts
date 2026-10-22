import { describe, expect, it } from "vitest";
import { CaptionTracker, type CaptionLine } from "./captionTracker.js";

const at = (second: number) => new Date(Date.UTC(2026, 9, 22, 11, 0, second));
const linesOf = (piece: Uint8Array): CaptionLine[] =>
  piece.byteLength === 0 ? [] : (JSON.parse(new TextDecoder().decode(piece)) as { lines: CaptionLine[] }).lines;

describe("CaptionTracker", () => {
  it("follows a turn as its text grows, as one line", () => {
    const tracker = new CaptionTracker();
    tracker.observe([{ speaker: "Priya", text: "I've been at" }], at(0));
    tracker.observe([{ speaker: "Priya", text: "I've been at Target Group for six years" }], at(2));

    const lines = linesOf(tracker.takePiece());
    expect(lines).toHaveLength(1);
    expect(lines[0]).toMatchObject({ speaker: "Priya", text: "I've been at Target Group for six years",
      startedAt: at(0).toISOString(), updatedAt: at(2).toISOString() });
  });

  it("opens a line for each new turn, and a piece carries only what changed since the last", () => {
    const tracker = new CaptionTracker();
    tracker.observe([{ speaker: "Yara", text: "Thanks for the time." }], at(0));
    expect(linesOf(tracker.takePiece())).toHaveLength(1);

    tracker.observe([
      { speaker: "Yara", text: "Thanks for the time." },
      { speaker: "Priya", text: "Happy to." },
    ], at(4));
    const second = linesOf(tracker.takePiece());
    expect(second.map((line) => line.speaker)).toEqual(["Priya"]);
    expect(second[0].id).toBe(2);

    expect(tracker.takePiece().byteLength).toBe(0);
  });

  it("keeps a turn's id when captions rewrite its last words", () => {
    const tracker = new CaptionTracker();
    tracker.observe([{ speaker: "Priya", text: "We moved to Dubai in twenty" }], at(0));
    tracker.observe([{ speaker: "Priya", text: "We moved to Dubai in 2019." }], at(2));
    const lines = linesOf(tracker.takePiece());
    expect(lines).toHaveLength(1);
    expect(lines[0].text).toBe("We moved to Dubai in 2019.");
  });
});
