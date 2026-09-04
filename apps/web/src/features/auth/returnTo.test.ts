import { beforeEach, describe, expect, it } from "vitest";
import { rememberReturnTo, safeReturnTo, takeReturnTo } from "./returnTo";

describe("where sign-in should land", () => {
  beforeEach(() => sessionStorage.clear());

  it("accepts an in-app path", () => {
    expect(safeReturnTo("/extension/connect")).toBe("/extension/connect");
    expect(safeReturnTo("/projects?tab=open")).toBe("/projects?tab=open");
  });

  it("refuses anything that would leave the app", () => {
    // Both are read by browsers as protocol-relative URLs, so both are off-site redirects.
    expect(safeReturnTo("//evil.example/phish")).toBeNull();
    expect(safeReturnTo("/\\evil.example/phish")).toBeNull();
    expect(safeReturnTo("https://evil.example")).toBeNull();
    expect(safeReturnTo(undefined)).toBeNull();
  });

  it("carries a destination across the OAuth round trip, once", () => {
    rememberReturnTo("/extension/connect");

    expect(takeReturnTo()).toBe("/extension/connect");
    expect(takeReturnTo()).toBeNull();
  });

  it("forgets an abandoned destination rather than redirecting a later sign-in with it", () => {
    rememberReturnTo("/extension/connect");
    rememberReturnTo(null);

    expect(takeReturnTo()).toBeNull();
  });

  it("refuses a stored destination that was tampered with", () => {
    sessionStorage.setItem("lightmove.returnTo", "//evil.example");

    expect(takeReturnTo()).toBeNull();
  });
});
