import { describe, expect, it } from "vitest";
import type { ApproachTouch, RecentApproach } from "../api/types";
import { approachLines, touchLine } from "./recentApproach";

const NOW = new Date("2026-11-14T10:00:00Z");

function touch(overrides: Partial<ApproachTouch>): ApproachTouch {
  return {
    kind: "EMAILED",
    byUserId: "u2",
    byName: "Sara Al-Mansour",
    at: "2026-11-02T10:00:00Z",
    projectId: "p2",
    positionTitle: "Head of Credit Risk",
    samePosition: false,
    byYou: false,
    ...overrides,
  };
}

describe("recent approach wording", () => {
  it("names who, how, when and where, and never the words", () => {
    expect(touchLine(touch({}), NOW)).toBe("Sara Al-Mansour emailed · 12 days ago · Head of Credit Risk");
    expect(touchLine(touch({ kind: "REPLIED" }), NOW)).toBe("Replied to Sara Al-Mansour · 12 days ago · Head of Credit Risk");
    expect(touchLine(touch({ kind: "WHATSAPP_LOGGED", samePosition: true }), NOW)).toBe(
      "Sara Al-Mansour logged a WhatsApp message · 12 days ago · this position",
    );
    expect(touchLine(touch({ byYou: true, kind: "CALL_LOGGED" }), NOW)).toBe(
      "You logged a call · 12 days ago · Head of Credit Risk",
    );
    expect(touchLine(touch({ at: "2026-11-15T09:00:00Z" }), NOW)).toBe(
      "Sara Al-Mansour emailed · scheduled · Head of Credit Risk",
    );
  });

  it("puts the window's touches first, then where they are in process, then the quiet history", () => {
    const approach: RecentApproach = {
      tier: "CAUTION",
      windowDays: 90,
      touches: [touch({ kind: "REPLIED" })],
      elsewhere: [
        { projectId: "p3", positionTitle: "COO", statusLabel: "Not interested", statusColour: "grey", inProcess: false,
          placed: false, addedByName: null, statusChangedAt: null, workable: true },
        { projectId: "p2", positionTitle: "Head of Credit Risk", statusLabel: "Interested", statusColour: "green",
          inProcess: true, placed: false, addedByName: "Sara Al-Mansour", statusChangedAt: null, workable: false },
      ],
      lastApproach: null,
    };
    expect(approachLines(approach, NOW)).toEqual([
      "Replied to Sara Al-Mansour · 12 days ago · Head of Credit Risk",
      "In process on Head of Credit Risk · Interested · added by Sara Al-Mansour",
      "Not interested on COO",
    ]);
  });
});
