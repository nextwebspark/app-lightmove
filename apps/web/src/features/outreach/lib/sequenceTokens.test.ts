import { describe, expect, it } from "vitest";
import { firstNameOf, render, renderParts } from "./sequenceTokens";

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
});
