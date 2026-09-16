import type { CandidateStatus } from "../../candidates/api/types";
import type { Disclosure, LevelCount, Report, SeniorityLevel, TalentHub } from "../api/types";

/**
 * A report as the server would answer it for a GCC food & beverage CFO search: 42 companies, 116
 * executives, in the shape `GET /projects/{id}/report` returns. The test fixture — the chapters agree
 * with each other, so a test can cross-check any two figures.
 */

const LEVELS: SeniorityLevel[] = ["Board", "C-Suite", "N-1", "N-2", "N-3"];

const WEEKLY = [8, 19, 24, 21, 20, 12, 8, 4];

/** Each week spread over its five working days, the Friday–Saturday weekend left empty; the last week is one day. */
function dailyFrom(weekly: number[], dayCount: number): number[] {
  const days: number[] = [];
  weekly.forEach((identified, week) => {
    const perDay = Math.floor(identified / 5);
    const remainder = identified - perDay * 5;
    for (let day = 0; day < 7 && days.length < dayCount; day += 1) {
      if (days.length === dayCount - 1 && week === weekly.length - 1) {
        days.push(identified);
      } else {
        days.push(day < 5 ? perDay + (day < remainder ? 1 : 0) : 0);
      }
    }
  });
  return days;
}

function levels(board: number, cSuite: number, n1: number, n2: number): LevelCount[] {
  return [
    { level: "Board", count: board },
    { level: "C-Suite", count: cSuite },
    { level: "N-1", count: n1 },
    { level: "N-2", count: n2 },
    { level: "N-3", count: 0 },
  ];
}

function hub(
  country: string,
  count: number,
  depth: LevelCount[],
  employers: string[],
  interested: number,
  gccNationals: number,
  female: number,
  recordedGender: number,
  medianPackage: number | null,
  point: [number, number] | null,
): TalentHub {
  return {
    country,
    count,
    depth,
    employers,
    interested,
    gccNationals,
    female,
    recordedGender,
    medianPackage,
    point: point === null ? null : { latitude: point[0], longitude: point[1] },
  };
}

function disclosure(
  id: string,
  fullName: string,
  company: string,
  title: string,
  country: string,
  nationality: string,
  status: CandidateStatus,
  totalPackage: number,
  fixed: number,
  note: string,
): Disclosure {
  return { id, fullName, company, title, country, nationality, status, fixed, totalPackage, note };
}

export const SAMPLE_REPORT: Report = {
  head: { universeCount: 42, executivesMapped: 116, truncated: false, generatedAt: "2026-09-08T07:40:00Z" },

  progress: {
    kickoff: "2026-07-21",
    targetDate: "2026-09-01",
    asOf: "2026-09-08",
    targetCompanies: 42,
    companiesCumulative: [0, 5, 11, 17, 23, 27, 29, 31],
    weekly: ["2026-07-27", "2026-08-03", "2026-08-10", "2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14"].map(
      (weekEnding, week) => ({ weekEnding, identified: WEEKLY[week] }),
    ),
    daily: dailyFrom(WEEKLY, 50),
    daysSinceLastCompany: 6,
  },

  market: {
    sectors: ["FMCG", "F&B", "Retail", "Agri", "Food Svc", "Other"],
    levels: LEVELS,
    cells: [
      ["FMCG", [3, 16, 13, 11]],
      ["F&B", [0, 12, 8, 6]],
      ["Retail", [0, 8, 6, 5]],
      ["Agri", [2, 7, 5, 0]],
      ["Food Svc", [0, 5, 4, 0]],
      ["Other", [0, 3, 2, 0]],
    ].flatMap(([sector, counts]) =>
      LEVELS.map((level, index) => ({
        sector: sector as string,
        level,
        count: (counts as number[])[index] ?? 0,
      })),
    ),
    withoutSector: 0,
    withoutSeniority: 0,
    slices: [
      {
        sector: "FMCG",
        level: "C-Suite",
        companies: ["Almarai", "Agthia Group", "IFFCO", "Pinehill Arabia"],
        executives: [
          { id: "e1", fullName: "Rami Khoury", company: "Almarai", status: "identified" },
          { id: "e2", fullName: "Sara Fadel", company: "Agthia Group", status: "engaged" },
          { id: "e3", fullName: "Hassan Noor", company: "IFFCO", status: "contacted" },
          { id: "e4", fullName: "Tariq Bahar", company: "Agthia Group", status: "interested" },
          { id: "e5", fullName: "Khalid Mansour", company: "Almarai", status: "offLimits" },
          { id: "e6", fullName: "Yara Kassem", company: "Pinehill Arabia", status: "interested" },
        ],
      },
      { sector: "FMCG", level: "N-1", companies: ["Almarai", "Agthia Group", "IFFCO"], executives: [] },
      { sector: "F&B", level: "C-Suite", companies: ["Savola Group", "Halwani Bros"], executives: [] },
    ],
    hubs: [
      hub("United Arab Emirates", 54, levels(2, 25, 18, 9), ["Unilever Gulf", "PepsiCo AMEA", "Agthia Group"], 19, 7, 15, 53, 1_150_000, [23.4241, 53.8478]),
      hub("Saudi Arabia", 46, levels(2, 21, 16, 7), ["Almarai", "NADEC", "Savola Foods"], 18, 27, 13, 45, 920_000, [23.8859, 45.0792]),
      hub("Kuwait", 8, levels(1, 4, 2, 1), ["Americana"], 3, 3, 2, 8, 1_100_000, [29.3117, 47.4818]),
      // No point: the geocoder has not placed it yet, so the map draws three pins and the bars all four.
      hub("Egypt", 5, levels(0, 1, 2, 2), ["Juhayna"], 2, 0, 3, 5, null, null),
    ],
    elsewhere: 3,
    unlocated: 0,
    companiesBySector: [
      { label: "FMCG", count: 14 },
      { label: "F&B", count: 9 },
      { label: "Retail", count: 7 },
      { label: "Agri", count: 6 },
      { label: "Food Svc", count: 4 },
      { label: "Other", count: 2 },
    ],
  },

  remuneration: {
    currency: "USD",
    fixedBand: { low: 560_000, high: 720_000 },
    packageBand: { low: 780_000, high: 1_100_000 },
    disclosures: [
      disclosure("d1", "H. Al-Zahrani", "Al Ain Farms", "VP Finance", "United Arab Emirates", "Saudi", "interested", 620_000, 450_000, "Smaller-company budget, comp was not a constraint."),
      disclosure("d2", "R. Al-Otaibi", "NADEC", "Finance Director", "Saudi Arabia", "Saudi", "interested", 700_000, 500_000, "Within band."),
      disclosure("d3", "M. Al-Dossari", "Savola Foods", "CFO", "Saudi Arabia", "Saudi", "interested", 780_000, 550_000, "At our floor."),
      disclosure("d4", "A. Al-Harbi", "Agthia Group", "CFO", "United Arab Emirates", "Saudi", "engaged", 850_000, 580_000, "Within band, reference checks underway."),
      disclosure("d5", "F. Al-Shammari", "IFFCO", "CFO", "United Arab Emirates", "Kuwaiti", "engaged", 920_000, 625_000, "Within band."),
      disclosure("d6", "N. Al-Ghamdi", "Almarai", "Regional CFO", "Saudi Arabia", "Saudi", "engaged", 980_000, 685_000, "Within band, second interview scheduled."),
      disclosure("d7", "Y. Al-Subaie", "Americana", "CFO", "Kuwait", "Saudi", "engaged", 1_100_000, 715_000, "Right at our package ceiling."),
      disclosure("d8", "L. Al-Amri", "Panda Retail", "CFO", "Saudi Arabia", "Emirati", "notInterested", 1_130_000, 770_000, "Cited compensation as the primary reason."),
      disclosure("d9", "T. Al-Rashidi", "Nestlé Middle East", "CFO", "United Arab Emirates", "Kuwaiti", "notInterested", 1_160_000, 675_000, "Priced above our package band, despite a fixed ask close to ours."),
      disclosure("d10", "D. Al-Anzi", "PepsiCo AMEA", "Regional CFO", "United Arab Emirates", "Kuwaiti", "engaged", 1_190_000, 665_000, "Still engaged despite the package gap — fixed expectations are within reach."),
      disclosure("d11", "K. Al-Muhanna", "Unilever Gulf", "CFO", "United Arab Emirates", "Kuwaiti", "notInterested", 1_220_000, 670_000, "The gap is almost entirely in bonus and LTI, not base."),
      disclosure("d12", "W. Al-Balawi", "Mondelez MENA", "CFO", "United Arab Emirates", "Saudi", "notInterested", 1_250_000, 700_000, "Declined on total package."),
      disclosure("d13", "B. Al-Otaibi", "Coca-Cola MENA", "Regional CFO", "Qatar", "Saudi", "outOfScope", 1_300_000, 780_000, "Accepted a competing offer."),
      disclosure("d14", "Z. Al-Harthi", "LuLu Group", "Group CFO", "United Arab Emirates", "Omani", "notInterested", 1_340_000, 938_000, "Significantly over budget on both measures."),
      disclosure("d15", "E. Al-Ghamdi", "Bel Group MENA", "Group CFO", "Saudi Arabia", "Saudi", "notInterested", 1_380_000, 800_000, "Outside a realistic range for this band."),
      disclosure("d16", "P. Al-Rasheed", "Kraft Heinz MENA", "Group CFO", "United Arab Emirates", "Emirati", "outOfScope", 1_450_000, 798_000, "Comp expectations disclosed upfront."),
    ],
    otherCurrency: 2,
  },

  diversity: {
    levels: LEVELS,
    nationalities: [
      { nationality: "Saudi", gcc: true, byLevel: levels(2, 14, 9, 5), unclassified: 0, total: 30 },
      { nationality: "Emirati", gcc: true, byLevel: levels(1, 9, 6, 3), unclassified: 0, total: 19 },
      { nationality: "Egyptian", gcc: false, byLevel: levels(1, 8, 6, 4), unclassified: 0, total: 19 },
      { nationality: "Lebanese", gcc: false, byLevel: levels(1, 7, 5, 3), unclassified: 0, total: 16 },
      { nationality: "Indian", gcc: false, byLevel: levels(0, 6, 5, 3), unclassified: 0, total: 14 },
      { nationality: "British", gcc: false, byLevel: levels(0, 4, 3, 2), unclassified: 0, total: 9 },
      { nationality: "Other", gcc: false, byLevel: levels(0, 3, 4, 2), unclassified: 0, total: 9 },
    ],
    unknownNationality: 0,
    gccNationals: 49,
    // Female per level matches the nationality rows' level totals: 5 / 51 / 38 / 22, with two
    // executives nobody has recorded a gender for, so the "recorded" denominator is visibly not
    // the headcount.
    genderByLevel: [
      { level: "Board", female: 1, male: 4, other: 0 },
      { level: "C-Suite", female: 14, male: 36, other: 0 },
      { level: "N-1", female: 13, male: 24, other: 1 },
      { level: "N-2", female: 9, male: 12, other: 0 },
      { level: "N-3", female: 0, male: 0, other: 0 },
    ],
    genderUnrecorded: 2,
  },
};
