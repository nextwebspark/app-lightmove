import { describe, expect, it } from "vitest";
import { inOrderOf, statusCountLabel, statusPreview } from "./pipelineStageRows";

describe("statusCountLabel", () => {
  it("counts one status in the singular and any other number in the plural", () => {
    expect(statusCountLabel(1)).toBe("1 status");
    expect(statusCountLabel(0)).toBe("0 statuses");
    expect(statusCountLabel(4)).toBe("4 statuses");
  });
});

describe("statusPreview", () => {
  it("names the first three statuses and counts the rest", () => {
    expect(statusPreview(["Contacted", "Follow-up sent", "Engaged", "Call booked"])).toBe(
      "Contacted, Follow-up sent, Engaged +1",
    );
    expect(statusPreview(["Screened", "Qualified", "References in progress"])).toBe(
      "Screened, Qualified, References in progress",
    );
  });

  it("says so when a stage has no statuses", () => {
    expect(statusPreview([])).toBe("No statuses yet");
  });
});

describe("inOrderOf", () => {
  it("orders rows as the ids name them, and puts a row they leave out last", () => {
    const rows = [{ id: "a" }, { id: "retired" }, { id: "b" }, { id: "c" }];
    expect(inOrderOf(rows, ["c", "a", "b"]).map((row) => row.id)).toEqual(["c", "a", "b", "retired"]);
  });
});
