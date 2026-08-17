import { describe, expect, it } from "vitest";
import { profileSchema } from "./profileSchema";

const valid = {
  fullName: "Alok Kumar",
  title: "Managing Partner",
  timezone: "Asia/Dubai",
  locale: "en",
};

describe("profileSchema", () => {
  it("accepts a filled profile", () => {
    expect(profileSchema.safeParse(valid).success).toBe(true);
  });

  it("trims what it accepts, so a pasted name is not stored with its spaces", () => {
    const parsed = profileSchema.parse({ ...valid, fullName: "  Alok Kumar  ", title: " Partner " });

    expect(parsed.fullName).toBe("Alok Kumar");
    expect(parsed.title).toBe("Partner");
  });

  it("refuses a nameless profile — a colleague identifies them by it", () => {
    const result = profileSchema.safeParse({ ...valid, fullName: "   " });

    expect(result.success).toBe(false);
    expect(issueFor(result, "fullName")).toBe("Enter your full name");
  });

  it("treats an empty title as a title, because none is allowed", () => {
    expect(profileSchema.safeParse({ ...valid, title: "" }).success).toBe(true);
  });

  it("refuses a name or title longer than the column holds", () => {
    expect(profileSchema.safeParse({ ...valid, fullName: "a".repeat(161) }).success).toBe(false);
    expect(profileSchema.safeParse({ ...valid, title: "a".repeat(121) }).success).toBe(false);
  });

  /** A stale tab offering a value the server no longer accepts fails here, with the field named. */
  it("refuses a timezone or language outside the pickers", () => {
    const zone = profileSchema.safeParse({ ...valid, timezone: "Mars/Olympus" });
    expect(issueFor(zone, "timezone")).toBe("Pick a timezone from the list");

    const language = profileSchema.safeParse({ ...valid, locale: "zz" });
    expect(issueFor(language, "locale")).toBe("Pick a language from the list");
  });
});

function issueFor(
  result: ReturnType<typeof profileSchema.safeParse>,
  field: string,
): string | undefined {
  return result.error?.issues.find((issue) => issue.path[0] === field)?.message;
}
