import { describe, expect, it } from "vitest";
import { isAllowedNavigation, isMeetLink } from "./meetLink.js";

describe("isMeetLink", () => {
  it.each([
    "https://meet.google.com/abc-defg-hij",
    "https://meet.google.com/abc-defg-hij?authuser=1",
  ])("opens %s", (link) => {
    expect(isMeetLink(link)).toBe(true);
  });

  it.each([
    "http://meet.google.com/abc-defg-hij",
    "https://meet.google.com.attacker.example/abc",
    "https://attacker.example/meet.google.com",
    "https://meet.google.com:8443/abc",
    "https://user:pass@meet.google.com/abc",
    "https://169.254.169.254/computeMetadata/v1/",
    "javascript:alert(1)",
    "not a link",
  ])("refuses %s", (link) => {
    expect(isMeetLink(link)).toBe(false);
  });
});

describe("isAllowedNavigation", () => {
  it("keeps the tab on Meet and Google's sign-in", () => {
    expect(isAllowedNavigation("https://meet.google.com/abc-defg-hij")).toBe(true);
    expect(isAllowedNavigation("https://accounts.google.com/ServiceLogin")).toBe(true);
    expect(isAllowedNavigation("https://attacker.example/")).toBe(false);
    expect(isAllowedNavigation("http://metadata.google.internal/computeMetadata/v1/")).toBe(false);
  });
});
