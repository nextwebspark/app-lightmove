import { describe, expect, it } from "vitest";
import {
  fallbacksIn,
  firstNameOf,
  render,
  renderLine,
  renderParts,
  templateTokenOptions,
  tokenOptions,
  tokenUsesIn,
  unfilledIn,
  unknownIn,
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

  it("offers the booking link only where booking pages are", () => {
    expect(tokenOptions(false).map((option) => option.token)).not.toContain("{{bookingLink}}");
    expect(tokenOptions(true).map((option) => option.token)).toContain("{{bookingLink}}");
  });

  it("uses a fallback only where the person has no value", () => {
    const sparse = { ...TOKENS, firstName: null, location: "  " };
    expect(render("Hi {{firstName|there}}, in {{ location | your city }}", sparse, null)).toBe("Hi there, in your city");
    expect(render("Hi {{firstName|  dear   reader }}", sparse, null)).toBe("Hi dear reader");
    expect(render("Hi {{firstName|there}}", TOKENS, null)).toBe("Hi Priya");
  });

  it("reads an escaped pipe, as the notes editor stores it, as a fallback", () => {
    expect(render("Hi {{firstName\\|there}}", { ...TOKENS, firstName: null }, null)).toBe("Hi there");
    expect(tokenUsesIn("{{firstName\\|there}}")).toEqual([{ name: "firstName", fallback: "there" }]);
  });

  it("leaves a fallback holding a brace or a second pipe as typed", () => {
    expect(render("{{firstName|a|b}}", TOKENS, null)).toBe("{{firstName|a|b}}");
  });

  it("keeps a subject on one line", () => {
    expect(renderLine("{{firstName}}: {{currentTitle}}", { ...TOKENS, currentTitle: "Chief\nFinancial  Officer" })).toBe(
      "Priya: Chief Financial Officer",
    );
  });

  it("names unknown and unfilled tokens in order of first use", () => {
    const text = "Hi {{firstName}} {{fristName}} {{location}} {{location|here}} {{opener}}";
    expect(unknownIn(text, ["firstName", "location"])).toEqual(["fristName", "opener"]);
    expect(unfilledIn(text, { ...TOKENS, location: null })).toEqual(["location"]);
    expect(unfilledIn("{{location|here}}", { ...TOKENS, location: null })).toEqual([]);
  });

  it("offers a template the sequence bar's tokens in its order, less the opener and the signature", () => {
    expect(templateTokenOptions(true).map((option) => option.token)).toEqual(
      tokenOptions(true)
        .map((option) => option.token)
        .filter((token) => token !== "{{opener}}" && token !== "{{signature}}"),
    );
  });

  it("marks a blank and a fallback standing in as their own parts, so a preview can show where each falls", () => {
    expect(renderParts("In {{location}}, {{location|here}}", { ...TOKENS, location: null }, null)).toEqual([
      { text: "In ", isOpener: false },
      { text: "", isOpener: false, isBlank: true, token: "location" },
      { text: ", ", isOpener: false },
      { text: "here", isOpener: false, isFallback: true, token: "location" },
    ]);
    expect(render("In {{location}}.", { ...TOKENS, location: null }, null)).toBe("In .");
    expect(render("Hi {{firstName|there}}", TOKENS, null)).toBe("Hi Priya");
  });

  it("names the variables a fallback stands in for, apart from the ones left blank", () => {
    const text = "{{firstName|there}} in {{location|your city}}, {{currentTitle}} and {{currentTitle|leader}}";
    const tokens = { ...TOKENS, location: null, currentTitle: null };
    expect(fallbacksIn(text, tokens)).toEqual(["location", "currentTitle"]);
    expect(unfilledIn(text, tokens)).toEqual(["currentTitle"]);
  });

  it("marks where the signature goes, leaving its token as typed in the words", () => {
    expect(renderParts("Thanks,\n{{ signature }}", TOKENS, null)).toEqual([
      { text: "Thanks,\n", isOpener: false },
      { text: "{{ signature }}", isOpener: false, isSignature: true },
    ]);
    expect(render("Re: {{signature}}", TOKENS, null)).toBe("Re: {{signature}}");
  });
});
