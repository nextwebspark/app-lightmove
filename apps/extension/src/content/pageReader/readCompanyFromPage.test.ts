import { readFileSync } from "node:fs";
import { join } from "node:path";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { readCompanyFromPage } from "./readCompanyFromPage";
import { companyDirectoryExtractor } from "./extractors/companyDirectoryExtractor";
import { linkedInCompanyExtractor } from "./extractors/linkedInCompanyExtractor";
import { structuredDataExtractor } from "./extractors/structuredDataExtractor";
import { mergeExtracted } from "./extractedCompany";

/**
 * Extractors are pure functions over a Document, which is what makes this possible at all: a saved
 * page, jsdom, no browser and no extension runtime.
 */
const FIXTURES = join(__dirname, "extractors", "__fixtures__");

/**
 * Built with JSDOM rather than `DOMParser`, because the page's own address is part of what the
 * extractors read — `DOMParser` produces a document at `about:blank` whose `location` cannot be
 * redefined, and the fallback that makes a corporate site's own domain work would never be exercised.
 */
function documentAt(fixture: string, url: string): Document {
  return htmlAt(readFileSync(join(FIXTURES, fixture), "utf8"), url);
}

function htmlAt(html: string, url: string): Document {
  return new JSDOM(html, { url }).window.document as unknown as Document;
}

describe("the LinkedIn company page", () => {
  const document = documentAt("linkedInCompanyPage.html", "https://www.linkedin.com/company/al-rawabi-dairy/");

  it("reads the company out of the About list", () => {
    expect(linkedInCompanyExtractor(document)).toMatchObject({
      companyName: "Al Rawabi Dairy Company",
      website: "https://alrawabidairy.ae",
      industry: "Food and Beverage Manufacturing",
      linkedinUrl: "https://www.linkedin.com/company/al-rawabi-dairy/",
    });
  });

  it("takes the low end of a headcount band, never the top", () => {
    // A mandate filtering on "at least 5,000 people" must not let in a company that has 1,001.
    expect(linkedInCompanyExtractor(document).numEmployees).toBe(1001);
  });

  it("splits the headquarters into a city and a country, dropping the region between them", () => {
    expect(linkedInCompanyExtractor(document)).toMatchObject({
      companyCity: "Dubai",
      companyCountry: "United Arab Emirates",
    });
  });

  it("does not make linkedin.com the company's own website", () => {
    // Filing every company captured on LinkedIn under linkedin.com would key them all to one domain
    // and collapse them into a single triage row.
    expect(readCompanyFromPage(document).website).toBe("https://alrawabidairy.ae");
  });

  it("says nothing about a page that is not a company page", () => {
    const elsewhere = documentAt("corporateSite.html", "https://alrawabidairy.ae/");
    expect(linkedInCompanyExtractor(elsewhere)).toEqual({});
  });
});

describe("an ordinary corporate site", () => {
  const document = documentAt("corporateSite.html", "https://alrawabidairy.ae/about");

  it("prefers the legal name from JSON-LD over the OpenGraph site name", () => {
    expect(structuredDataExtractor(document).companyName).toBe("Al Rawabi Dairy Company LLC");
  });

  it("finds the Organization inside a @graph, not just at the top level", () => {
    expect(structuredDataExtractor(document)).toMatchObject({
      companyCity: "Dubai",
      companyCountry: "United Arab Emirates",
      numEmployees: 1200,
    });
  });

  it("picks the LinkedIn company URL out of sameAs and ignores the other profiles", () => {
    expect(structuredDataExtractor(document).linkedinUrl).toBe(
      "https://www.linkedin.com/company/al-rawabi-dairy/",
    );
  });

  it("falls back to the address bar for a site that names no url of its own", () => {
    const bare = htmlAt("<html><body><h1>Acme</h1></body></html>", "https://acme.sa/about");
    expect(readCompanyFromPage(bare).website).toBe("https://acme.sa");
  });
});

describe("merging what several extractors read", () => {
  it("takes the first non-empty value per field, so order is priority", () => {
    const merged = mergeExtracted([
      { companyName: "Specific" },
      { companyName: "Generic", industry: "Dairy" },
    ]);
    expect(merged).toMatchObject({ companyName: "Specific", industry: "Dairy" });
  });

  it("lets a later extractor fill a gap but never overwrite an answer", () => {
    // The property that makes adding a new extractor safe: appending one cannot degrade a page an
    // existing extractor already understood.
    const merged = mergeExtracted([{ companyName: "Kept", website: null }, { website: "https://filled.ae" }]);
    expect(merged.companyName).toBe("Kept");
    expect(merged.website).toBe("https://filled.ae");
  });

  it("answers nulls rather than undefined when nothing was read", () => {
    expect(mergeExtracted([])).toMatchObject({ companyName: null, numEmployees: null });
  });
});

describe("a company directory page", () => {
  const document = documentAt("companyDirectoryPage.html", "https://app.apollo.io/companies/zenith");

  it("reads facts out of a definition list, a table and a div pair alike", () => {
    // The extractor keys on the label, not the markup, because these sites restyle often and a
    // class-name selector would not survive it.
    expect(companyDirectoryExtractor(document)).toMatchObject({
      companyName: "Zenith Industrial",
      website: "zenith-industrial.sa",
      industry: "Industrial Machinery",
      description: "Heavy machinery for the GCC construction sector.",
    });
  });

  it("takes the headcount as a number, commas and all", () => {
    expect(companyDirectoryExtractor(document).numEmployees).toBe(2400);
  });

  it("splits the headquarters, dropping the province between city and country", () => {
    expect(companyDirectoryExtractor(document)).toMatchObject({
      companyCity: "Riyadh",
      companyCountry: "Saudi Arabia",
    });
  });

  it("does not mistake prose beginning with a label for a fact row", () => {
    // The length guard earns its place here: without it, "Industry analysts have covered this
    // company…" would overwrite a real industry with a sentence.
    expect(companyDirectoryExtractor(document).industry).toBe("Industrial Machinery");
  });

  it("says nothing at all about a page that is not a directory page", () => {
    // Narrow by host on purpose: "the text next to the word Industry" is reliable on a fact table and
    // reckless on a page of prose.
    const elsewhere = documentAt("corporateSite.html", "https://alrawabidairy.ae/");
    expect(companyDirectoryExtractor(elsewhere)).toEqual({});
  });

  it("does not let the directory's own domain become the company's website", () => {
    expect(readCompanyFromPage(document).website).toBe("zenith-industrial.sa");
  });
});
