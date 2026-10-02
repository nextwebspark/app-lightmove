import { describe, expect, it } from "vitest";
import {
  bookingLinkPreviewOf,
  firstNameOf,
  render,
  renderParts,
  tokenOptions,
  usesBookingLink,
} from "./sequenceTokens";

const TOKENS = {
  firstName: "Priya",
  currentTitle: "CFO",
  currentCompany: "Target Group",
  positionTitle: "Group CFO",
  location: "Dubai",
  senderFirstName: "Yara",
};

describe("sequence tokens", () => {
  it("fills every token as the server does", () => {
    expect(
      render(
        "Hi {{firstName}}, {{ opener }} {{currentTitle}} at {{currentCompany}}, {{positionTitle}} in {{location}}. {{senderFirstName}}",
        TOKENS,
        "Your move stood out.",
      ),
    ).toBe("Hi Priya, Your move stood out. CFO at Target Group, Group CFO in Dubai. Yara");
  });

  it("leaves an unknown token as typed", () => {
    expect(render("Hi {{fristName}}", TOKENS, null)).toBe("Hi {{fristName}}");
    expect(render("{{constructor}} {{toString}}", TOKENS, null)).toBe("{{constructor}} {{toString}}");
  });

  it("marks where the opener landed", () => {
    expect(renderParts("Hi {{firstName}},\n\n{{opener}}\n\nBye", TOKENS, "Line.")).toEqual([
      { text: "Hi Priya,\n\n", isOpener: false },
      { text: "Line.", isOpener: true },
      { text: "\n\nBye", isOpener: false },
    ]);
  });

  it("greets by the first word of a name", () => {
    expect(firstNameOf("  Priya  Raman")).toBe("Priya");
    expect(firstNameOf(" ")).toBeNull();
  });

  it("draws the booking link as its own part, and leaves it as typed where no link is known", () => {
    const parts = renderParts("Pick a time: {{ bookingLink }}", { ...TOKENS, bookingLink: "beta.uncava.com/book/yara" }, null);
    expect(parts).toEqual([
      { text: "Pick a time: ", isOpener: false },
      { text: "beta.uncava.com/book/yara", isOpener: false, isLink: true },
    ]);
    expect(render("Pick a time: {{bookingLink}}", TOKENS, null)).toBe("Pick a time: {{bookingLink}}");
  });

  it("offers the booking link only where booking pages are, and knows a template that asks for it", () => {
    expect(tokenOptions(false).map((option) => option.token)).not.toContain("{{bookingLink}}");
    expect(tokenOptions(true).map((option) => option.token)).toContain("{{bookingLink}}");
    expect(usesBookingLink("Pick a time: {{ bookingLink }}")).toBe(true);
    expect(usesBookingLink("Hi {{firstName}}")).toBe(false);
    expect(usesBookingLink(null)).toBe(false);
  });

  it("previews the link a sender's name will get, accents and all", () => {
    expect(bookingLinkPreviewOf("Yara Haddad", "beta.uncava.com")).toBe("beta.uncava.com/book/yara-haddad");
    expect(bookingLinkPreviewOf("Zoë Ünal", "beta.uncava.com")).toBe("beta.uncava.com/book/zoe-unal");
    expect(bookingLinkPreviewOf(null, "beta.uncava.com")).toBe("beta.uncava.com/book/your-name");
  });
});
