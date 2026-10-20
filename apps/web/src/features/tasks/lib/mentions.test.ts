import { describe, expect, it } from "vitest";
import { mentionToken, parseNoteBody, type Mention } from "./mentions";

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
});

describe("a mention's token", () => {
  it("keeps a bracket in a name from breaking its token", () => {
    expect(mentionToken({ kind: "user", userId: "u-3", name: "Omar [Ops]" })).toBe("@[Omar Ops](user:u-3)");
  });
});
