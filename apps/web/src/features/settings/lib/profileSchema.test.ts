import { describe, expect, it } from "vitest";
import { profileSchema, profileSchemaFor } from "./profileSchema";

const valid = {
  fullName: "Alok Kumar",
  title: "Managing Partner",
  timezone: "Asia/Dubai",
  dateFormat: "",
  timeFormat: "",
  locale: "en",
  phone: "",
  linkedinUrl: "",
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

  it("takes a phone as people write one and a LinkedIn profile however it was pasted", () => {
    expect(profileSchema.safeParse({ ...valid, phone: "+971 50 123 4567" }).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, phone: "(04) 123-4567" }).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, linkedinUrl: "linkedin.com/in/yara-haddad" }).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, linkedinUrl: "https://ae.linkedin.com/in/yara?trk=x" }).success).toBe(true);
  });

  it("refuses a phone that is not a number and an address that is not a LinkedIn profile", () => {
    expect(issueFor(profileSchema.safeParse({ ...valid, phone: "call me" }), "phone")).toBe("Enter a phone number");
    expect(issueFor(profileSchema.safeParse({ ...valid, phone: "12+34567" }), "phone")).toBe("Enter a phone number");
    expect(issueFor(profileSchema.safeParse({ ...valid, linkedinUrl: "linkedin.com/company/x" }), "linkedinUrl")).toMatch(
      /LinkedIn profile/,
    );
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
  it("refuses a timezone, format or language outside the pickers", () => {
    for (const refused of ["Mars/Olympus", "EST", "Etc/GMT", "America/Argentina/Buenos_Aires"]) {
      const zone = profileSchema.safeParse({ ...valid, timezone: refused });
      expect(issueFor(zone, "timezone")).toBe("Pick a timezone from the list");
    }
    expect(profileSchema.safeParse({ ...valid, timezone: "America/Sao_Paulo" }).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, timezone: "UTC" }).success).toBe(true);
    expect(issueFor(profileSchema.safeParse({ ...valid, dateFormat: "D/M/Y" }), "dateFormat")).toBe(
      "Pick a date format from the list",
    );
    expect(issueFor(profileSchema.safeParse({ ...valid, timeFormat: "H36" }), "timeFormat")).toBe(
      "Pick a time format from the list",
    );

    const language = profileSchema.safeParse({ ...valid, locale: "zz" });
    expect(issueFor(language, "locale")).toBe("Pick a language from the list");
  });

  it("takes an empty choice as the workspace's", () => {
    expect(profileSchema.safeParse({ ...valid, timezone: "", dateFormat: "", timeFormat: "" }).success).toBe(true);
    expect(profileSchema.safeParse({ ...valid, dateFormat: "MMM_D_YYYY", timeFormat: "H12" }).success).toBe(true);
  });

  it("keeps a zone saved before the fixed list, while a new choice must be listed", () => {
    const saved = profileSchemaFor("US/Pacific");
    expect(saved.safeParse({ ...valid, timezone: "US/Pacific" }).success).toBe(true);
    expect(issueFor(saved.safeParse({ ...valid, timezone: "EST" }), "timezone")).toBe("Pick a timezone from the list");
  });
});

function issueFor(
  result: ReturnType<typeof profileSchema.safeParse>,
  field: string,
): string | undefined {
  return result.error?.issues.find((issue) => issue.path[0] === field)?.message;
}
