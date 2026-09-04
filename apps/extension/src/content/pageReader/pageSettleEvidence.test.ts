import { describe, expect, it } from "vitest";
import { settleEvidenceOf } from "./pageSettleEvidence";
import type { PageSubject } from "./readPageSubject";

function profileRead(overrides: Partial<PageSubject> & { fullName?: string | null } = {}): PageSubject {
  const { fullName = "Amira Haddad", ...rest } = overrides;
  return {
    subject: "person",
    person: { fullName, linkedinUrl: null },
    company: { companyName: null, linkedinUrl: null },
    pageUrl: "https://www.linkedin.com/in/bilal-nasser/",
    declaredUrl: null,
    ...rest,
  };
}

describe("deciding whether a read describes the page it was taken at", () => {
  it("accepts a name with nothing said against it, so a panel opened on a profile does not wait", () => {
    expect(settleEvidenceOf(profileRead(), null)).toBe("settled");
  });

  it("refuses a read whose page still declares another profile", () => {
    const read = profileRead({ declaredUrl: "https://www.linkedin.com/in/amira-haddad/" });

    expect(settleEvidenceOf(read, null)).toBe("displaced");
  });

  it("waits rather than refusing when the page has named nobody yet", () => {
    const read = profileRead({ fullName: null, declaredUrl: null });

    expect(settleEvidenceOf(read, null)).toBe("waiting");
  });

  it("refuses the previous profile's name still on screen after a navigation", () => {
    const previous = { pageKey: "person:amira-haddad", name: "Amira Haddad" };

    expect(settleEvidenceOf(profileRead(), previous)).toBe("displaced");
  });

  it("accepts the same name read again at the same page, so a title blink is not a navigation", () => {
    const previous = { pageKey: "person:bilal-nasser", name: "Amira Haddad" };

    expect(settleEvidenceOf(profileRead(), previous)).toBe("settled");
  });

  it("accepts a page it cannot capture from at all, which has nothing to wait for", () => {
    const read = profileRead({ subject: "unknown", pageUrl: "https://www.linkedin.com/feed/" });

    expect(settleEvidenceOf(read, null)).toBe("settled");
  });

  it("judges a company page by its own name", () => {
    const read: PageSubject = {
      subject: "company",
      person: { fullName: null, linkedinUrl: null },
      company: { companyName: "Al Rawabi", linkedinUrl: null },
      pageUrl: "https://www.linkedin.com/company/zenith/",
      declaredUrl: null,
    };

    expect(settleEvidenceOf(read, { pageKey: "company:al-rawabi", name: "Al Rawabi" })).toBe("displaced");
    expect(settleEvidenceOf(read, null)).toBe("settled");
  });
});
