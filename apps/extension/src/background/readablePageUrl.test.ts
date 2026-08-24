import { describe, expect, it } from "vitest";
import { isReadablePageUrl } from "./readablePageUrl";

describe("isReadablePageUrl", () => {
  it("accepts an ordinary web page", () => {
    expect(isReadablePageUrl("https://alrawabidairy.ae")).toBe(true);
    expect(isReadablePageUrl("http://alrawabidairy.ae/about")).toBe(true);
  });

  it("refuses the pages Chrome will not inject into", () => {
    expect(isReadablePageUrl("chrome://extensions")).toBe(false);
    expect(isReadablePageUrl("about:blank")).toBe(false);
    expect(isReadablePageUrl("file:///Users/someone/notes.html")).toBe(false);
    expect(isReadablePageUrl("https://chrome.google.com/webstore/detail/x")).toBe(false);
  });

  it("treats a missing url as unreadable rather than throwing", () => {
    expect(isReadablePageUrl(undefined)).toBe(false);
    expect(isReadablePageUrl(null)).toBe(false);
    expect(isReadablePageUrl("")).toBe(false);
  });
});
