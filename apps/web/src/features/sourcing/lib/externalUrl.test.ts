import { describe, expect, it } from "vitest";
import { externalUrl, linkLabel } from "./externalUrl";

describe("externalUrl — a warehouse string is not an href until it survives this", () => {
  it("gives a bare host the safe scheme", () => {
    expect(externalUrl("meridian.com")).toBe("https://meridian.com/");
    expect(externalUrl("linkedin.com/company/meridian")).toBe(
      "https://linkedin.com/company/meridian",
    );
  });

  it("leaves an already-qualified URL alone", () => {
    expect(externalUrl("https://meridian.com/about")).toBe("https://meridian.com/about");
    expect(externalUrl("http://meridian.com/")).toBe("http://meridian.com/");
  });

  it("refuses a scheme that is not http or https", () => {
    // The pipeline owns this column. Rendered straight into an href, this is one click from running.
    expect(externalUrl("javascript:alert(1)")).toBeNull();
    expect(externalUrl("data:text/html,<script>alert(1)</script>")).toBeNull();
    expect(externalUrl("file:///etc/passwd")).toBeNull();
  });

  it("treats an absent or unparseable value as no link", () => {
    expect(externalUrl(null)).toBeNull();
    expect(externalUrl(undefined)).toBeNull();
    expect(externalUrl("   ")).toBeNull();
    expect(externalUrl("http://")).toBeNull();
  });
});

describe("linkLabel", () => {
  it("strips the scheme, the www, and a trailing slash", () => {
    expect(linkLabel("https://www.meridian.com/")).toBe("meridian.com");
    expect(linkLabel("meridian.com")).toBe("meridian.com");
  });
});
