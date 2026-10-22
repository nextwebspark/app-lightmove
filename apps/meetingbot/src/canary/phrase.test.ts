import { existsSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { CANARY_CLIP_PATH, CANARY_PHRASE, hearingOf } from "./phrase.js";

describe("hearingOf", () => {
  it("hears the phrase however the transcriber punctuates or slightly mishears it", () => {
    expect(hearingOf(CANARY_PHRASE)).toBe("phrase");
    expect(hearingOf("purple giraffe reads quiet library book every morning purple giraffes read")).toBe("phrase");
    expect(hearingOf("Purple. Giraffes read quite library books, every mourning!")).toBe("phrase");
  });

  it("tells other words from nothing at all", () => {
    expect(hearingOf("Sorry, can you hear me? I think my microphone is off.")).toBe("other-words");
    expect(hearingOf("purple library")).toBe("other-words");
    expect(hearingOf("")).toBe("nothing");
    expect(hearingOf(" ... ")).toBe("nothing");
  });

  it("finds the committed clip where the speaker is pointed at it", () => {
    expect(existsSync(CANARY_CLIP_PATH)).toBe(true);
  });
});
