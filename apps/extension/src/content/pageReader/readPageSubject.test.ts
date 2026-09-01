import { readFileSync } from "node:fs";
import { join } from "node:path";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { readPageSubject } from "./readPageSubject";
import { linkedInCompanyExtractor } from "./extractors/linkedInCompanyExtractor";
import { linkedInProfileExtractor } from "./extractors/linkedInProfileExtractor";
import { isLinkedInPageUrl, companySlugOf, profileSlugOf } from "./linkedInUrls";

const FIXTURES = join(__dirname, "extractors", "__fixtures__");

function documentAt(fixture: string, url: string): Document {
  return new JSDOM(readFileSync(join(FIXTURES, fixture), "utf8"), { url }).window.document as unknown as Document;
}

describe("the LinkedIn profile", () => {
  it("reads the name and the canonical profile URL", () => {
    const document = documentAt("linkedInProfilePage.html", "https://www.linkedin.com/in/amira-haddad/");

    expect(linkedInProfileExtractor(document)).toMatchObject({
      fullName: "Amira Haddad",
      linkedinUrl: "https://www.linkedin.com/in/amira-haddad/",
    });
  });

  it("reads the 2025 layout, which has no h1, no metadata and hashed class names", () => {
    // The tab title is the anchor there: "(3) Name - Headline - Employer | LinkedIn".
    const document = documentAt("linkedInProfileHashedLayout.html", "https://www.linkedin.com/in/amira-haddad/");

    expect(linkedInProfileExtractor(document).fullName).toBe("Amira Haddad");
  });

  it("reads the signed-out layout too, which is a different page", () => {
    const document = documentAt("linkedInProfilePagePublic.html", "https://www.linkedin.com/in/amira-haddad");

    expect(linkedInProfileExtractor(document).fullName).toBe("Amira Haddad");
  });

  it("is keyed on the host the browser loaded, so a page cannot declare itself a profile", () => {
    const impostor = new JSDOM(
      '<link rel="canonical" href="https://www.linkedin.com/in/amira-haddad" /><h1>Amira Haddad</h1>',
      { url: "https://impostor.example/profile" },
    ).window.document as unknown as Document;

    expect(linkedInProfileExtractor(impostor)).toEqual({});
  });
});

describe("the LinkedIn company page", () => {
  const document = documentAt("linkedInCompanyPage.html", "https://www.linkedin.com/company/al-rawabi-dairy/");

  it("reads the name and the canonical company URL", () => {
    expect(linkedInCompanyExtractor(document)).toMatchObject({
      companyName: "Al Rawabi Dairy Company",
      linkedinUrl: "https://www.linkedin.com/company/al-rawabi-dairy/",
    });
  });

  it("says nothing about a page that is not a company page", () => {
    const elsewhere = documentAt("linkedInProfilePage.html", "https://www.linkedin.com/in/amira-haddad/");
    expect(linkedInCompanyExtractor(elsewhere)).toEqual({});
  });
});

describe("classifying the page", () => {
  it("reads a LinkedIn profile as a person, by its URL", () => {
    const read = readPageSubject(documentAt("linkedInProfilePage.html", "https://www.linkedin.com/in/amira-haddad/"));
    expect(read.subject).toBe("person");
    expect(read.person.fullName).toBe("Amira Haddad");
  });

  it("reads a LinkedIn company page as a company, by its URL", () => {
    const read = readPageSubject(
      documentAt("linkedInCompanyPage.html", "https://www.linkedin.com/company/al-rawabi-dairy/"),
    );
    expect(read.subject).toBe("company");
  });

  it("says unknown for a LinkedIn page that names nobody — the feed, search, jobs", () => {
    const feed = new JSDOM("<main>Feed</main>", { url: "https://www.linkedin.com/feed/" })
      .window.document as unknown as Document;
    expect(readPageSubject(feed).subject).toBe("unknown");
  });
});

describe("recognising LinkedIn URLs", () => {
  it("accepts any http(s) linkedin.com page and refuses everything else", () => {
    expect(isLinkedInPageUrl("https://www.linkedin.com/feed/")).toBe(true);
    expect(isLinkedInPageUrl("https://linkedin.com/in/someone")).toBe(true);
    expect(isLinkedInPageUrl("https://acme.sa/about")).toBe(false);
    expect(isLinkedInPageUrl("https://notlinkedin.com/in/someone")).toBe(false);
    expect(isLinkedInPageUrl("chrome://extensions")).toBe(false);
    expect(isLinkedInPageUrl(undefined)).toBe(false);
  });

  it("takes the slug from the address bar and only there", () => {
    expect(profileSlugOf("https://www.linkedin.com/in/amira-haddad/details/experience/")).toBe("amira-haddad");
    expect(profileSlugOf("https://www.linkedin.com/company/acme/")).toBeNull();
    expect(companySlugOf("https://www.linkedin.com/company/al-rawabi-dairy/about/")).toBe("al-rawabi-dairy");
    expect(companySlugOf("https://evil.sa/?next=linkedin.com/company/acme")).toBeNull();
  });
});
