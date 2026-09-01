import { beforeEach, describe, expect, it } from "vitest";
import { collectReportContext } from "./reportContext";

/**
 * The context is reproduction detail, with one exception that is not: the page URL, which on the
 * screens a tester is most likely to report from carries a live credential.
 */
describe("collectReportContext", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("keeps the path and the ordinary query", () => {
    window.history.replaceState({}, "", "/projects/abc/companies/universe?page=3&sort=name");

    expect(collectReportContext().pageUrl).toBe("/projects/abc/companies/universe?page=3&sort=name");
  });

  /**
   * The one that matters. A reset link is a password change in the hands of whoever reads it, and the
   * reset screen is exactly where a tester reports "this page did nothing" — into a public repository.
   */
  it("redacts the token out of an emailed link", () => {
    window.history.replaceState({}, "", "/auth/reset-password?token=a-live-256-bit-secret");

    const { pageUrl } = collectReportContext();

    expect(pageUrl).toBe("/auth/reset-password?token=%5Bredacted%5D");
    expect(pageUrl).not.toContain("a-live-256-bit-secret");
  });

  it("redacts an OAuth code and state, and drops the fragment entirely", () => {
    window.history.replaceState({}, "", "/auth/callback?code=xyz&state=abc&next=/team#access_token=live");

    const { pageUrl } = collectReportContext();

    expect(pageUrl).not.toContain("xyz");
    expect(pageUrl).not.toContain("abc");
    expect(pageUrl).not.toContain("access_token");
    // Everything that is not a credential survives, so the report still says where they were.
    expect(pageUrl).toContain("next=%2Fteam");
  });

  it("reports the theme the tester was actually looking at", () => {
    document.body.classList.add("dark");
    expect(collectReportContext().theme).toBe("dark");

    document.body.classList.remove("dark");
    expect(collectReportContext().theme).toBe("light");
  });
});
