import { describe, expect, it } from "vitest";
import { confirmationOf, doneOf } from "./privacyPageCopy";

describe("privacy page copy", () => {
  it("names the firm once it has named itself", () => {
    expect(confirmationOf("optOut", "Meridian Search").title).toBe("Stop emails from Meridian Search?");
    expect(doneOf("copy", "Meridian Search").body).toMatch(/^Meridian Search's privacy team has your request/);
  });

  it("speaks as us, never as a firm called We, before the firm has named itself", () => {
    expect(confirmationOf("optOut", null).title).toBe("Stop emails from us?");
    expect(doneOf("deletion", null).body).toMatch(/^Our privacy team has your request/);
    expect(doneOf("optOut", null).body).toMatch(/^We have recorded/);
  });

  it("keeps a firm that is really called We as its name", () => {
    expect(confirmationOf("optOut", "We").title).toBe("Stop emails from We?");
  });
});
