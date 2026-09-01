import { describe, expect, it } from "vitest";
import { httpUrlOrNull, isLinkedInCompanyUrl } from "./extractedCompany";

/**
 * Every URL an extractor yields is text a hostile page controls, and it is stored rather than merely
 * shown — so a `javascript:` href planted today would detonate on whichever future screen turns a
 * stored website into a link. These pin the boundary that stops it.
 */
describe("a URL read off a page", () => {
  it("keeps an http(s) URL exactly as the page wrote it", () => {
    // Not `URL.href`: that normalises, and a trailing slash appearing on every stored value is churn
    // no reader asked for.
    expect(httpUrlOrNull("https://alrawabidairy.ae")).toBe("https://alrawabidairy.ae");
    expect(httpUrlOrNull("http://acme.sa/about")).toBe("http://acme.sa/about");
  });

  it("keeps a bare domain, which is what a directory's Website row usually holds", () => {
    expect(httpUrlOrNull("zenith-industrial.sa")).toBe("zenith-industrial.sa");
  });

  it("refuses every scheme but http and https", () => {
    expect(httpUrlOrNull("javascript:alert(document.cookie)")).toBeNull();
    expect(httpUrlOrNull("data:text/html;base64,PHNjcmlwdD4=")).toBeNull();
    expect(httpUrlOrNull("vbscript:msgbox(1)")).toBeNull();
    expect(httpUrlOrNull("  JavaScript:alert(1)  ")).toBeNull();
  });

  it("resolves a relative href against its page, since alone it means nothing", () => {
    expect(httpUrlOrNull("/about", "https://acme.sa/team")).toBe("https://acme.sa/about");
  });

  it("has nothing to say about an empty value", () => {
    expect(httpUrlOrNull(null)).toBeNull();
    expect(httpUrlOrNull("   ")).toBeNull();
  });
});

describe("recognising a LinkedIn company URL", () => {
  it("accepts the real thing", () => {
    expect(isLinkedInCompanyUrl("https://www.linkedin.com/company/al-rawabi")).toBe(true);
    expect(isLinkedInCompanyUrl("https://linkedin.com/company/acme/about")).toBe(true);
  });

  it("refuses a payload that merely contains the path", () => {
    // The substring test this replaced was satisfied by exactly this string.
    expect(isLinkedInCompanyUrl("javascript:void(0)/*linkedin.com/company/*/")).toBe(false);
    expect(isLinkedInCompanyUrl("https://notlinkedin.com/company/acme")).toBe(false);
    expect(isLinkedInCompanyUrl("https://evil.sa/?next=linkedin.com/company/acme")).toBe(false);
  });

  it("refuses a LinkedIn URL that is not a company page", () => {
    expect(isLinkedInCompanyUrl("https://www.linkedin.com/in/someone")).toBe(false);
  });
});
