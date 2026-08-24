import { describe, expect, it } from "vitest";
import { companyDomainName, isReadablePageUrl } from "./companyDomainName";

/**
 * This is the key a captured company is stored under, so it has to agree with the API's
 * `WebsiteDomain.of` exactly. A disagreement does not fail loudly — it files one company twice.
 */
describe("companyDomainName", () => {
  it("strips the scheme, www and the path", () => {
    expect(companyDomainName("https://www.alrawabidairy.ae/en/about")).toBe("alrawabidairy.ae");
  });

  it("accepts a bare host", () => {
    expect(companyDomainName("alrawabidairy.ae")).toBe("alrawabidairy.ae");
  });

  it("lower-cases, because a domain is case-insensitive and a storage key is not", () => {
    expect(companyDomainName("HTTPS://WWW.AlRawabiDairy.AE")).toBe("alrawabidairy.ae");
  });

  it("keeps a subdomain that is not www — sa.example.com is not example.com", () => {
    expect(companyDomainName("https://sa.example.com")).toBe("sa.example.com");
  });

  it("drops the port and any userinfo rather than storing them as part of the domain", () => {
    expect(companyDomainName("https://user@example.com:8443/path")).toBe("example.com");
  });

  it("answers nothing for a value that is not a domain", () => {
    expect(companyDomainName("localhost")).toBeNull();
    expect(companyDomainName("   ")).toBeNull();
    expect(companyDomainName(null)).toBeNull();
    expect(companyDomainName("not a url at all")).toBeNull();
  });
});

describe("isReadablePageUrl", () => {
  it("accepts an ordinary web page", () => {
    expect(isReadablePageUrl("https://alrawabidairy.ae")).toBe(true);
  });

  it("refuses the pages Chrome will not inject into", () => {
    expect(isReadablePageUrl("chrome://extensions")).toBe(false);
    expect(isReadablePageUrl("about:blank")).toBe(false);
    expect(isReadablePageUrl("https://chrome.google.com/webstore/detail/x")).toBe(false);
    expect(isReadablePageUrl(undefined)).toBe(false);
  });
});
