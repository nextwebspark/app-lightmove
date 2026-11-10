import { afterEach, describe, expect, it } from "vitest";
import { forgetScorecardDrafts, scorecardDraftKey } from "./scorecardDrafts";

describe("forgetScorecardDrafts", () => {
  afterEach(() => sessionStorage.clear());

  it("removes every candidate's draft and leaves the tab's other keys alone", () => {
    sessionStorage.setItem(scorecardDraftKey("c-1"), "{}");
    sessionStorage.setItem(scorecardDraftKey("c-2"), "{}");
    sessionStorage.setItem("lm.position-receipts.p-1", "{}");

    forgetScorecardDrafts();

    expect(sessionStorage.getItem(scorecardDraftKey("c-1"))).toBeNull();
    expect(sessionStorage.getItem(scorecardDraftKey("c-2"))).toBeNull();
    expect(sessionStorage.getItem("lm.position-receipts.p-1")).toBe("{}");
  });
});
