import { describe, expect, it } from "vitest";
import { toBrowsableUrl } from "./url";

/**
 * The client half of a rule the server also enforces, so the cases below are the ones
 * `CapturedCompanyDetails` is written against: the two must not drift.
 */
describe("toBrowsableUrl", () => {
  it("keeps an absolute http(s) URL as it was given", () => {
    expect(toBrowsableUrl("https://acwapower.com/about")).toBe("https://acwapower.com/about");
    expect(toBrowsableUrl("http://acwapower.com")).toBe("http://acwapower.com");
  });

  it("promotes a bare host, which is what people actually type", () => {
    expect(toBrowsableUrl("acwapower.com")).toBe("https://acwapower.com");
    expect(toBrowsableUrl("  acwapower.com  ")).toBe("https://acwapower.com");
  });

  it("refuses a scheme a browser should not follow", () => {
    expect(toBrowsableUrl("javascript:alert(1)")).toBeNull();
    expect(toBrowsableUrl("data:text/html,<script>alert(1)</script>")).toBeNull();
    expect(toBrowsableUrl("file:///etc/passwd")).toBeNull();
  });

  it("does not let the promotion launder a rejected scheme", () => {
    // "javascript:alert(1)" carries no "://", so it would otherwise become
    // "https://javascript:alert(1)" — which parses, but to no host.
    expect(toBrowsableUrl("javascript:alert(1)")).toBeNull();
  });

  it("treats absent as absent", () => {
    expect(toBrowsableUrl(null)).toBeNull();
    expect(toBrowsableUrl(undefined)).toBeNull();
    expect(toBrowsableUrl("   ")).toBeNull();
  });
});
