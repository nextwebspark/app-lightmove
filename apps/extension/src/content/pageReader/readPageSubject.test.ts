import { readFileSync } from "node:fs";
import { join } from "node:path";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { readPageSubject } from "./readPageSubject";
import { linkedInProfileExtractor } from "./extractors/linkedInProfileExtractor";
import { structuredPersonExtractor } from "./extractors/structuredPersonExtractor";

const FIXTURES = join(__dirname, "extractors", "__fixtures__");

function documentAt(fixture: string, url: string): Document {
  return new JSDOM(readFileSync(join(FIXTURES, fixture), "utf8"), { url }).window.document as unknown as Document;
}

describe("the LinkedIn profile", () => {
  it("reads the person, the current role and the ones before it", () => {
    const document = documentAt("linkedInProfilePage.html", "https://www.linkedin.com/in/amira-haddad/");

    expect(linkedInProfileExtractor(document)).toMatchObject({
      fullName: "Amira Haddad",
      title: "Group Chief Financial Officer at Al Rawabi Dairy",
      employerName: "Al Rawabi Dairy",
      location: "Dubai, United Arab Emirates",
      tenure: "Mar 2021 - Present · 4 yrs 5 mos",
      linkedinUrl: "https://www.linkedin.com/in/amira-haddad/",
      career: [
        { title: "Finance Director", company: "Agthia Group", period: "2016 - 2021 · 5 yrs" },
        { title: "Head of FP&A", company: "Almarai", period: "2012 - 2016 · 4 yrs" },
      ],
    });
  });

  it("reads the signed-out layout too, which is a different page", () => {
    const document = documentAt("linkedInProfilePagePublic.html", "https://www.linkedin.com/in/amira-haddad");

    expect(linkedInProfileExtractor(document)).toMatchObject({
      fullName: "Amira Haddad",
      title: "Group Chief Financial Officer",
      employerName: "Al Rawabi Dairy",
      tenure: "Mar 2021 - Present",
    });
  });

  it("is keyed on the host the browser loaded, so a page cannot declare itself a profile", () => {
    const impostor = new JSDOM(
      '<link rel="canonical" href="https://www.linkedin.com/in/amira-haddad" /><h1>Amira Haddad</h1>',
      { url: "https://impostor.example/profile" },
    ).window.document as unknown as Document;

    expect(linkedInProfileExtractor(impostor)).toEqual({});
  });
});

describe("a bio page's structured data", () => {
  const document = documentAt("personStructuredData.html", "https://zenith-industrial.sa/leadership/khalid");

  it("reads the Person node, joining the name parts and unwrapping the mailto", () => {
    expect(structuredPersonExtractor(document)).toMatchObject({
      fullName: "Khalid Al Mutairi",
      title: "Chief Operating Officer",
      employerName: "Zenith Industrial Holding",
      location: "Riyadh, Saudi Arabia",
      email: "k.almutairi@zenith-industrial.sa",
      phone: "+966 11 123 4567",
      linkedinUrl: "https://www.linkedin.com/in/khalid-al-mutairi",
    });
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

  // Both are on the page; the consultant standing on a bio is looking at the person.
  it("prefers the person on a bio page that also describes its company", () => {
    const read = readPageSubject(documentAt("personStructuredData.html", "https://zenith-industrial.sa/leadership"));
    expect(read.subject).toBe("person");
    expect(read.company.companyName).toBe("Zenith Industrial Holding");
  });

  it("reads a corporate site as a company", () => {
    expect(readPageSubject(documentAt("corporateSite.html", "https://alrawabidairy.ae/about")).subject)
      .toBe("company");
  });

  // A name-shaped og:title is what every article has. Unknown leaves the tab alone rather than guessing.
  it("says nothing about a page that is neither", () => {
    const article = new JSDOM('<meta property="og:title" content="Amira Haddad" /><p>News</p>', {
      url: "https://news.example/story",
    }).window.document as unknown as Document;

    expect(readPageSubject(article).subject).toBe("unknown");
  });
});
