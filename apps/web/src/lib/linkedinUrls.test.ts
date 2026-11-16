import { describe, expect, it } from "vitest";
import { isLinkedInProfileUrl, profileSlugOf } from "./linkedinUrls";

describe("LinkedIn profile links", () => {
  it("reads the member slug whatever the scheme, subdomain or trailing parts", () => {
    expect(profileSlugOf("linkedin.com/in/Omar-Haddad")).toBe("omar-haddad");
    expect(profileSlugOf(" https://uk.linkedin.com/in/omar-haddad/?trk=share ")).toBe("omar-haddad");
  });

  it("refuses a company page, a search and another host", () => {
    expect(isLinkedInProfileUrl("https://www.linkedin.com/company/almarai/")).toBe(false);
    expect(isLinkedInProfileUrl("https://www.linkedin.com/search/results/people/?keywords=cfo")).toBe(false);
    expect(isLinkedInProfileUrl("https://notlinkedin.com/in/omar")).toBe(false);
    expect(isLinkedInProfileUrl("")).toBe(false);
  });
});
