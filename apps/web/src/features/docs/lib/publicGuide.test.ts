import { describe, expect, it } from "vitest";
import { publicGuide } from "./publicGuide";

const GUIDE = [
  "# Connect",
  "",
  "Paste `https://beta.uncava.com/api/v1/mcp`.",
  "",
  "## For Uncava maintainers",
  "",
  "- the signing key lives in Secret Manager",
  "",
].join("\n");

describe("publicGuide", () => {
  it("leaves the maintainers' notes off", () => {
    expect(publicGuide(GUIDE, "https://beta.uncava.com")).not.toContain("Secret Manager");
  });

  it("names the server on the origin the guide is read at", () => {
    expect(publicGuide(GUIDE, "http://localhost:5173/")).toContain("`http://localhost:5173/api/v1/mcp`");
  });

  it("keeps a guide with no maintainers' section whole", () => {
    expect(publicGuide("# Only\n\nText.\n", "https://x.example")).toBe("# Only\n\nText.\n");
  });
});
