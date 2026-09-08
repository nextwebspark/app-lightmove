import { describe, expect, it } from "vitest";
import { careerSummary, groupCareer, isCurrent, parsePeriod, tenureOf } from "./careerTimeline";

const post = (company: string | null, title: string | null, period: string | null) => ({
  company,
  title,
  period,
});

/**
 * A period is free text, so what matters is that the shapes sources actually write come out right
 * and everything else comes out null — never a guess dressed as a fact beside the raw text.
 */
describe("parsePeriod", () => {
  it("reads the shapes enrichment and researchers write", () => {
    expect(parsePeriod("Jan 2021 – Present")).toEqual({
      start: { year: 2021, month: 1 },
      end: "present",
    });
    expect(parsePeriod("2017–2021")).toEqual({ start: { year: 2017 }, end: { year: 2021 } });
    expect(parsePeriod("2017 - 2021")).toEqual({ start: { year: 2017 }, end: { year: 2021 } });
    expect(parsePeriod("Mar 2019 – Aug 2022")).toEqual({
      start: { year: 2019, month: 3 },
      end: { year: 2022, month: 8 },
    });
    expect(parsePeriod("September 2019 to Aug 2022")).toEqual({
      start: { year: 2019, month: 9 },
      end: { year: 2022, month: 8 },
    });
    // One provider writes an open tenure as a dash with nothing after it.
    expect(parsePeriod("2020 –")).toEqual({ start: { year: 2020 }, end: "present" });
  });

  it("keeps a lone year as a point rather than a range", () => {
    expect(parsePeriod("2021")).toEqual({ start: { year: 2021 }, end: null });
  });

  it("answers null to anything it cannot read, without throwing", () => {
    expect(parsePeriod("c. 2015")).toBeNull();
    expect(parsePeriod("12 yrs 8 mos")).toBeNull();
    expect(parsePeriod("Mob 2020 – 2021")).toBeNull();
    expect(parsePeriod("2019 – 2020 – 2021")).toBeNull();
    expect(parsePeriod("")).toBeNull();
    expect(parsePeriod(null)).toBeNull();
  });
});

describe("tenureOf", () => {
  const today = new Date(2026, 8, 8);

  it("counts months inclusively when both ends carry one", () => {
    expect(tenureOf(parsePeriod("Jan 2021 – Mar 2021"), today)).toBe("3 mos");
    expect(tenureOf(parsePeriod("Mar 2019 – Aug 2022"), today)).toBe("3 yrs 6 mos");
    expect(tenureOf(parsePeriod("Jan 2021 – Present"), today)).toBe("5 yrs 9 mos");
  });

  it("never invents months for a year range", () => {
    expect(tenureOf(parsePeriod("2017–2021"), today)).toBe("4 yrs");
    expect(tenureOf(parsePeriod("2025 – Present"), today)).toBe("1 yr");
    expect(tenureOf(parsePeriod("2020 – Jan 2021"), today)).toBe("1 yr");
  });

  it("has nothing to say about a point, a same-year range or an unread period", () => {
    expect(tenureOf(parsePeriod("2021"), today)).toBeNull();
    expect(tenureOf(parsePeriod("2021 – 2021"), today)).toBeNull();
    expect(tenureOf(null, today)).toBeNull();
  });
});

describe("isCurrent", () => {
  it("flags an open-ended post however the source spelled it", () => {
    expect(isCurrent("Jan 2021 – Present")).toBe(true);
    expect(isCurrent("2019 - present")).toBe(true);
    expect(isCurrent("since 2019, current")).toBe(true);
    expect(isCurrent("2020 –")).toBe(true);
    expect(isCurrent("2017–2021")).toBe(false);
    expect(isCurrent(null)).toBe(false);
  });
});

describe("groupCareer", () => {
  it("folds consecutive posts at one employer, keeping the stored order", () => {
    const groups = groupCareer([
      post("Almarai", "CFO", "2021 – Present"),
      post("almarai ", "Finance Director", "2017 – 2021"),
      post("Regional Foods", "Controller", "2012 – 2017"),
      post("Almarai", "Analyst", "2008 – 2012"),
    ]);

    expect(groups.map((group) => [group.company, group.posts.length])).toEqual([
      ["Almarai", 2],
      ["Regional Foods", 1],
      ["Almarai", 1],
    ]);
    expect(groups[0].posts.map((entry) => entry.title)).toEqual(["CFO", "Finance Director"]);
  });

  it("never merges posts that name no employer", () => {
    const groups = groupCareer([post(null, "Advisor", null), post(null, "Board member", null)]);
    expect(groups).toHaveLength(2);
  });
});

describe("careerSummary", () => {
  it("leads with the current post, falling back to the first", () => {
    expect(
      careerSummary([post("Regional Foods", "Controller", "2012 – 2017"), post("Almarai", "CFO", "2021 – Present")]),
    ).toBe("CFO at Almarai · 2 posts");
    expect(careerSummary([post("Almarai", null, "2017 – 2021")])).toBe("Almarai · 1 post");
    expect(careerSummary([])).toBeNull();
  });
});
