import { afterEach, describe, expect, it, vi } from "vitest";
import { copyText } from "./clipboard";

describe("copyText", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("answers false where there is no clipboard, rather than throwing", async () => {
    vi.stubGlobal("navigator", {});
    await expect(copyText("+971 50 000 0000")).resolves.toBe(false);
  });

  it("answers true once the clipboard took the value", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { clipboard: { writeText } });

    await expect(copyText("yasmin@example.com")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("yasmin@example.com");
  });
});
