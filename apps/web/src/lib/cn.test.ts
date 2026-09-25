import { describe, expect, it } from "vitest";
import { cn } from "./cn";

describe("cn", () => {
  it("keeps a type-scale size beside a colour", () => {
    expect(cn("text-note font-semibold", "text-u-inferred")).toBe("text-note font-semibold text-u-inferred");
  });

  it("lets a later type-scale size replace an earlier one", () => {
    expect(cn("text-body", "text-meta")).toBe("text-meta");
  });

  it("keeps a type role beside a colour and an alignment", () => {
    expect(cn("type-label text-u-text2", "text-end")).toBe("type-label text-u-text2 text-end");
  });
});
