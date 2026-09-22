import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Receipts } from "./documentFill";
import { clearReceipts, loadReceipts, saveReceipts } from "./receiptStore";

const RECEIPTS: Receipts = {
  brief: {
    fileName: "cfo-brief.pdf",
    scalars: { department: { previousValue: null, previousSource: "TEMPLATE", confidence: "high", snippet: "Finance" } },
    lists: {},
  },
};

describe("receiptStore", () => {
  beforeEach(() => sessionStorage.clear());

  it("reads back what it saved for the same project and document", () => {
    saveReceipts("p1", "cfo-brief.pdf", RECEIPTS);
    expect(loadReceipts("p1", "cfo-brief.pdf")).toEqual(RECEIPTS);
  });

  it("answers nothing for another project, or for a document that has since been replaced", () => {
    saveReceipts("p1", "cfo-brief.pdf", RECEIPTS);
    expect(loadReceipts("p2", "cfo-brief.pdf")).toEqual({});
    expect(loadReceipts("p1", "coo-brief.pdf")).toEqual({});
    expect(loadReceipts("p1", undefined)).toEqual({});
  });

  it("clears the row rather than storing an empty reading", () => {
    saveReceipts("p1", "cfo-brief.pdf", RECEIPTS);
    saveReceipts("p1", "cfo-brief.pdf", {});
    expect(sessionStorage.length).toBe(0);

    saveReceipts("p1", "cfo-brief.pdf", RECEIPTS);
    saveReceipts("p1", undefined, RECEIPTS);
    expect(sessionStorage.length).toBe(0);
  });

  it("survives storage that refuses to answer", () => {
    saveReceipts("p1", "cfo-brief.pdf", RECEIPTS);
    const refuse = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(loadReceipts("p1", "cfo-brief.pdf")).toEqual({});
    refuse.mockRestore();

    const full = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });
    expect(() => saveReceipts("p1", "cfo-brief.pdf", RECEIPTS)).not.toThrow();
    full.mockRestore();
  });

  it("answers nothing for a row that is not a stored reading", () => {
    sessionStorage.setItem("lightmove.position.receipts.p1", "not json");
    expect(loadReceipts("p1", "cfo-brief.pdf")).toEqual({});

    clearReceipts("p1");
    expect(sessionStorage.length).toBe(0);
  });
});
