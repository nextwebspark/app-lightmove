import { describe, expect, it } from "vitest";
import { listOfNames, providerLabel } from "./accountDeletion";

describe("accountDeletion", () => {
  it("names the workspaces a person leaves as a sentence would", () => {
    expect(listOfNames(["Alpha"])).toBe("Alpha");
    expect(listOfNames(["Alpha", "Beta"])).toBe("Alpha and Beta");
    expect(listOfNames(["Alpha", "Beta", "Gamma"])).toBe("Alpha, Beta and Gamma");
    expect(listOfNames(["Google", "LinkedIn"], "or")).toBe("Google or LinkedIn");
  });

  it("labels a provider by its own name, and one it does not know by its id", () => {
    expect(providerLabel("google")).toBe("Google");
    expect(providerLabel("linkedin")).toBe("LinkedIn");
    expect(providerLabel("okta")).toBe("Okta");
  });
});
