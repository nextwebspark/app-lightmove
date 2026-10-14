import { describe, expect, it } from "vitest";
import {
  displayTextOf,
  mentionsIn,
  mentionsPresent,
  mentionToken,
  parseNoteBody,
  serializeMentions,
  type Mention,
} from "./mentions";

const SARA_ID = "7b1c2d3e-0000-4000-8000-000000000001";
const sara: Mention = { kind: "user", userId: SARA_ID, name: "Sara Al-Mansour" };
const team: Mention = { kind: "team" };

describe("a note body's mentions", () => {
  it("reads a user token and the team token out of the words around them", () => {
    expect(parseNoteBody(`Spoke to her. @[Sara Al-Mansour](user:${SARA_ID}) can you call? cc @[team](team)`)).toEqual([
      { kind: "text", text: "Spoke to her. " },
      { kind: "mention", mention: sara },
      { kind: "text", text: " can you call? cc " },
      { kind: "mention", mention: team },
    ]);
  });

  it("leaves a body with no tokens as one run of text, an @ in prose included", () => {
    expect(parseNoteBody("Email her at layla@example.com")).toEqual([
      { kind: "text", text: "Email her at layla@example.com" },
    ]);
  });

  it("leaves a half-written token as the words it is", () => {
    expect(parseNoteBody("@[Sara Al-Mansour](user:)")).toEqual([{ kind: "text", text: "@[Sara Al-Mansour](user:)" }]);
  });

  it("lists everyone mentioned once, in the order they first appear", () => {
    expect(mentionsIn(`@[team](team) then ${mentionToken(sara)} and ${mentionToken(sara)}`)).toEqual([team, sara]);
  });
});

describe("the composer's text", () => {
  it("writes each picked @Name as its token when saved, and leaves an unpicked @ alone", () => {
    expect(serializeMentions("@Sara Al-Mansour please call; @team note; @Omar unpicked", [sara, team])).toBe(
      `${mentionToken(sara)} please call; @[team](team) note; @Omar unpicked`,
    );
  });

  it("writes the longer of two names that share a start whole", () => {
    const sar: Mention = { kind: "user", userId: "u-2", name: "Sara" };

    expect(serializeMentions("@Sara Al-Mansour and @Sara", [sar, sara])).toBe(
      `${mentionToken(sara)} and @[Sara](user:u-2)`,
    );
  });

  it("keeps a bracket in a name from breaking its token", () => {
    expect(mentionToken({ kind: "user", userId: "u-3", name: "Omar [Ops]" })).toBe("@[Omar Ops](user:u-3)");
  });

  it("counts a mention only while its @Name is still in the words", () => {
    expect(mentionsPresent("Ask @team", [sara, team])).toEqual([team]);
  });

  it("turns a stored body back into what the composer shows, and back again unchanged", () => {
    const stored = `Call ${mentionToken(sara)} and @[team](team)`;

    expect(displayTextOf(stored)).toBe("Call @Sara Al-Mansour and @team");
    expect(serializeMentions(displayTextOf(stored), mentionsIn(stored))).toBe(stored);
  });
});
