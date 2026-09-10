import { describe, expect, it } from "vitest";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../../triage/api/types";
import type { MapLocation, TalentMapPage } from "../api/types";
import { buildTree, filterTree, nodesOf, pathTo, UNLOCATED_KEY } from "./talentMapTree";

const company = (overrides: Partial<TriageCompany>): TriageCompany => ({
  id: "u1",
  apolloAccountId: "a1",
  source: "strategy",
  status: "inUniverse",
  note: null,
  companyName: "ACWA Power",
  industry: "oil & energy",
  companyCountry: "Saudi Arabia",
  companyCity: "Riyadh",
  numEmployees: 3000,
  annualRevenue: null,
  website: null,
  companyLinkedinUrl: null,
  foundedYear: null,
  shortDescription: null,
  sourceUrl: null,
  logoUrl: null,
  customFields: {},
  addedAt: "2026-08-01T09:00:00Z",
  ...overrides,
});

const person = (overrides: Partial<Candidate>): Candidate => ({
  id: "c1",
  triageCompanyId: "u1",
  companyName: "ACWA Power",
  fullName: "Yasmin El-Sayed",
  title: "VP Finance",
  seniority: "N-1",
  status: "engaged",
  email: null,
  phone: null,
  linkedinUrl: null,
  locationCountry: null,
  locationCity: null,
  nationality: null,
  yearsExperience: null,
  summary: null,
  note: null,
  compensation: {
    currency: null, baseSalary: null, bonus: null, allowances: null,
    longTermIncentive: null, noticePeriod: null,
  },
  career: [],
  education: [],
  skills: [],
  languages: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00Z",
  enrichedAt: null,
  ...overrides,
});

const riyadh: MapLocation = { latitude: 24.7, longitude: 46.7, precision: "CITY", placeLabel: "Riyadh, Saudi Arabia", country: "Saudi Arabia", countryCode: "SA" };
const dubai: MapLocation = { latitude: 25.3, longitude: 55.3, precision: "CITY", placeLabel: "Dubai, United Arab Emirates", country: "United Arab Emirates", countryCode: "AE" };
const oman: MapLocation = { latitude: 21, longitude: 57, precision: "COUNTRY", placeLabel: "Oman", country: "Oman", countryCode: "OM" };

const page: TalentMapPage = {
  companies: [
    company({ id: "u1", companyName: "ACWA Power" }),
    company({ id: "u2", companyName: "Almarai", numEmployees: 40000 }),
    company({ id: "u3", companyName: "Emaar", companyCountry: "United Arab Emirates", companyCity: "Dubai" }),
    company({ id: "u4", companyName: "Gulf Trader", companyCountry: null, companyCity: null }),
  ],
  totalCompanies: 4,
  candidates: [
    person({ id: "c1", fullName: "Yasmin El-Sayed", seniority: "N-1" }),
    person({ id: "c2", fullName: "Ahmed Bakr", seniority: "C-Suite", title: "CEO" }),
    person({ id: "c3", triageCompanyId: "u3", companyName: "Emaar", fullName: "Omar Haddad", locationCity: "Riyadh", locationCountry: "Saudi Arabia" }),
    person({ id: "c4", triageCompanyId: null, companyName: "Untriaged Co", fullName: "Lina Said", locationCountry: "Oman" }),
    person({ id: "c5", triageCompanyId: null, companyName: "Nowhere", fullName: "Wei Ling Tan" }),
    person({ id: "c6", triageCompanyId: "u4", companyName: "Gulf Trader", fullName: "Sara Noor" }),
  ],
  totalCandidates: 6,
  locations: { u1: riyadh, u2: riyadh, u3: riyadh, c3: riyadh, c4: oman },
  geocodingPending: 0,
};

describe("buildTree", () => {
  it("groups country → company → executives, most companies first, people by seniority", () => {
    const tree = buildTree(page);

    expect(tree.countries.map((country) => country.name)).toEqual(["Saudi Arabia", "Oman"]);
    const saudi = tree.countries[0];
    expect(saudi.companies.map((c) => c.company.companyName)).toEqual(["ACWA Power", "Almarai", "Emaar"]);
    // The C-Suite executive leads the N-1, whatever order they were mapped in.
    expect(saudi.companies[0].executives.map((e) => e.candidate.fullName)).toEqual(["Ahmed Bakr", "Yasmin El-Sayed"]);
    expect(saudi.companyCount).toBe(3);
    expect(saudi.executiveCount).toBe(3);
  });

  it("seats an executive without a place at their company, and draws one with a place at it", () => {
    const tree = buildTree(page);
    const acwa = tree.countries[0].companies[0];
    expect(acwa.executives[0].seatedAt).toBe("u1");
    expect(acwa.executives[0].location).toBeNull();

    const emaar = tree.countries[0].companies[2];
    expect(emaar.executives[0].location).toEqual(riyadh);
    expect(emaar.executives[0].seatedAt).toBeNull();
  });

  it("files an unmapped executive under their own country, and the placeless under No location", () => {
    const tree = buildTree(page);
    const oman = tree.countries[1];
    expect(oman.companies).toHaveLength(0);
    expect(oman.unmapped.map((e) => e.candidate.fullName)).toEqual(["Lina Said"]);

    expect(tree.unlocated.companies.map((c) => c.company.companyName)).toEqual(["Gulf Trader"]);
    expect(tree.unlocated.executives.map((e) => e.candidate.fullName)).toEqual(["Wei Ling Tan"]);
    // A person at a placeless company stays listed under it and counts as unlocated.
    expect(tree.unlocated.companies[0].executives[0].candidate.fullName).toBe("Sara Noor");
  });

  it("counts what it holds, located and not", () => {
    const tree = buildTree(page);
    expect(tree.counts).toEqual({ countries: 2, companies: 4, executives: 6, unlocated: 3 });
  });

  it("files a company under the country it was drawn in, not the one its snapshot names", () => {
    // Emaar's snapshot says Dubai; its only executive is in Riyadh, so that is where the server drew
    // it — and where the panel must list it. Grouping on the snapshot put a Saudi pin under the UAE.
    const tree = buildTree(page);
    const emaar = tree.countries[0].companies[2];

    expect(emaar.company.companyCountry).toBe("United Arab Emirates");
    expect(tree.countries[0].name).toBe("Saudi Arabia");
    expect(tree.countries.map((country) => country.name)).not.toContain("United Arab Emirates");
  });

  it("folds two spellings of one country into the group the server put a code to", () => {
    const tree = buildTree({
      ...page,
      companies: [
        company({ id: "u5", companyName: "Emaar", companyCountry: "United Arab Emirates" }),
        // No country resolved for this one — an executive with a city and no country beside it — so
        // it falls back to its own spelling, which must still land in the group above.
        company({ id: "u6", companyName: "Majid Al Futtaim", companyCountry: "UNITED ARAB EMIRATES " }),
      ],
      candidates: [],
      locations: { u5: dubai, u6: { ...dubai, country: null, countryCode: null } },
    });

    expect(tree.countries.map((country) => country.name)).toEqual(["United Arab Emirates"]);
    expect(tree.countries[0].key).toBe("ae");
  });

  it("files a located company with no country of its own under the country of its point", () => {
    const tree = buildTree({
      ...page,
      companies: [company({ id: "u7", companyName: "Gulf Trader", companyCountry: null, companyCity: null })],
      candidates: [],
      locations: { u7: oman },
    });

    expect(tree.countries.map((country) => country.name)).toEqual(["Oman"]);
    expect(tree.unlocated.companies).toHaveLength(0);
  });

  it("counts an executive filed under a country with no point as unlocated, not as placed", () => {
    // The country name alone files her under Kuwait; nothing resolved it, so no pin is drawn and the
    // panel must say so rather than counting her as placed.
    const tree = buildTree({
      companies: [],
      totalCompanies: 0,
      candidates: [person({ id: "c9", triageCompanyId: null, fullName: "Nadia Karim", locationCountry: "Kuwait" })],
      totalCandidates: 1,
      locations: {},
      geocodingPending: 1,
    });

    expect(tree.countries[0].unmapped.map((e) => e.candidate.fullName)).toEqual(["Nadia Karim"]);
    expect(tree.counts).toEqual({ countries: 1, companies: 0, executives: 1, unlocated: 1 });
  });
});

describe("filterTree", () => {
  it("keeps a company that matches with all its people, and a matching person with their company", () => {
    const tree = buildTree(page);

    const byCompany = filterTree(tree, "acwa");
    expect(byCompany.countries).toHaveLength(1);
    expect(byCompany.countries[0].companies[0].executives).toHaveLength(2);

    const byPerson = filterTree(tree, "yasmin");
    expect(byPerson.countries[0].companies.map((c) => c.company.companyName)).toEqual(["ACWA Power"]);
    expect(byPerson.countries[0].companies[0].executives.map((e) => e.candidate.fullName)).toEqual(["Yasmin El-Sayed"]);
    expect(byPerson.counts.companies).toBe(1);
    expect(byPerson.counts.executives).toBe(1);
  });

  it("drops a country left with nothing, and answers the unfiltered tree for a blank search", () => {
    const tree = buildTree(page);
    expect(filterTree(tree, "lina").countries.map((c) => c.name)).toEqual(["Oman"]);
    expect(filterTree(tree, "   ")).toBe(tree);
  });
});

describe("pathTo / nodesOf", () => {
  it("names the branches above a row so a pin click can open them", () => {
    const tree = buildTree(page);
    expect(pathTo(tree, "c1")).toEqual({ country: "sa", company: "u1" });
    expect(pathTo(tree, "u3")).toEqual({ country: "sa", company: null });
    expect(pathTo(tree, "c4")).toEqual({ country: "om", company: null });
    expect(pathTo(tree, "c6")).toEqual({ country: UNLOCATED_KEY, company: "u4" });
    expect(pathTo(tree, "nope")).toEqual({ country: null, company: null });
    expect(nodesOf(tree).map((node) => node.id)).toEqual(["u1", "c2", "c1", "u2", "u3", "c3", "c4", "u4", "c6", "c5"]);
  });
});
