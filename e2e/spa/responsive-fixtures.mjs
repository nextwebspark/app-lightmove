/**
 * Just enough of the API to render every screen, so the sweep needs no database.
 *
 * Payloads are deliberately awkward — long names, full teams, wide numbers. A fixture that fits
 * comfortably would let real overflow through.
 */

const WORKSPACE = {
  id: "ws-1",
  name: "Meridian Executive Partners",
  slug: "meridian",
  logoMark: "M",
  emailDomain: "meridian-partners.com",
  roles: ["ADMIN", "MEMBER"],
  joinedAt: "2026-03-01T00:00:00Z",
};

export const USER = {
  id: "user-1",
  email: "ada.lovelace@meridian-partners.com",
  fullName: "Ada Lovelace-Kensington",
  title: "Managing Partner",
  avatarUrl: null,
  emailVerified: true,
  hasPassword: true,
  timezone: "Asia/Dubai",
  locale: "en",
  workspace: WORKSPACE,
};

const team = (n) =>
  Array.from({ length: n }, (_, i) => ({
    memberId: `member-${i + 1}`,
    userId: i === 0 ? USER.id : `user-${i + 2}`,
    fullName: ["Ada Lovelace-Kensington", "Yara Haddad", "Tomas Brennan", "Wei Zhang"][i % 4],
    email: `person${i}@meridian-partners.com`,
    avatarUrl: null,
    projectRoles: [i === 0 ? "LEAD" : "RESEARCHER"],
  }));

export const PROJECTS = [
  {
    id: "proj-1",
    clientId: "client-1",
    clientName: "Emirates Global Aluminium",
    positionTitle: "Chief Operating Officer, Downstream Manufacturing",
    stage: "MAPPING",
    health: "OK",
    targetDate: "2026-11-30",
    team: team(4),
    representatives: [],
    companies: 128,
    candidates: 42,
    createdAt: "2026-04-01T00:00:00Z",
  },
  {
    id: "proj-2",
    clientId: "client-2",
    clientName: "Qatar National Petrochemical",
    positionTitle: "Group Head of Digital Transformation",
    stage: "OUTREACH",
    health: "RISK",
    targetDate: "2026-09-15",
    team: team(2),
    representatives: [],
    companies: 64,
    candidates: 17,
    createdAt: "2026-05-12T00:00:00Z",
  },
];

const MEMBERS = team(4).map((seat, i) => ({
  memberId: seat.memberId,
  userId: seat.userId,
  fullName: seat.fullName,
  email: seat.email,
  avatarUrl: null,
  title: "Principal Consultant",
  roles: i === 0 ? ["ADMIN", "MEMBER"] : ["MEMBER"],
  status: "ACTIVE",
  joinedAt: "2026-03-01T00:00:00Z",
}));

const CLIENTS = [
  {
    id: "client-1",
    name: "Emirates Global Aluminium",
    type: "RETAINED",
    sector: "Metals & Mining",
    hqCountry: "United Arab Emirates",
    activeMandates: 3,
    deliveredMandates: 7,
    contacts: [{ id: "rep-1", fullName: "Noura Al Mansoori", avatarUrl: null, status: "ACTIVE" }],
    viewers: { total: 2, names: ["Noura Al Mansoori", "Khalid Rahman"] },
  },
  {
    id: "client-2",
    name: "Qatar National Petrochemical",
    type: "PROSPECT",
    sector: "Energy & Chemicals",
    hqCountry: "Qatar",
    activeMandates: 1,
    deliveredMandates: 2,
    contacts: [],
    viewers: { total: 0, names: [] },
  },
];

const COMPANY = (i) => ({
  apolloAccountId: `apollo-${i}`,
  companyName: `Gulf Industrial Holdings Company ${i}`,
  industry: "Industrial Manufacturing & Engineering Services",
  companyCountry: "United Arab Emirates",
  companyCity: "Abu Dhabi",
  numEmployees: 1000 + i * 37,
  annualRevenue: i % 3 === 0 ? 250000000 + i * 1000 : null,
  website: `https://gulf-industrial-${i}.com`,
  logoUrl: null,
  shortDescription: "Diversified industrial group operating across the Gulf Cooperation Council.",
  foundedYear: 1998,
  companyLinkedinUrl: null,
  facebookUrl: null,
  twitterUrl: null,
  companyPhone: "+971 2 555 0100",
  companyState: "Abu Dhabi",
  companyAddress: "Corniche Road, Al Bateen Tower, Floor 24",
  parentCompany: null,
  totalFunding: null,
  latestFunding: null,
  latestFundingAmount: null,
  lastRaisedAt: null,
  numberOfRetailLocations: null,
  keywords: ["manufacturing", "logistics", "petrochemicals", "engineering"],
  technologies: ["SAP", "Salesforce", "Oracle Cloud", "Microsoft Azure"],
  sicCodes: ["3341"],
  naicsCodes: ["331314"],
});

const COMPANIES = Array.from({ length: 12 }, (_, i) => COMPANY(i + 1));

const FACETS = {
  sectorGroups: [
    {
      name: "Industrials",
      industries: [
        { value: "manufacturing", label: "Manufacturing", count: 6100 },
        { value: "oil & energy", label: "Oil & Energy", count: 4300 },
      ],
    },
  ],
  adjacentIndustries: {},
  marketSegments: [{ value: "enterprise", label: "Enterprise", count: 3200 }],
  employeeBands: [
    { value: "1-10", label: "1-10", count: 12000 },
    { value: "1001-2000", label: "1001-2000", count: 900 },
  ],
  revenueBands: [{ value: "1M-10M", label: "1M-10M", count: 8000 }],
};

const EMPTY_FILTER = {
  industries: [],
  keywords: [],
  marketSegments: [],
  countries: [],
  employeeBands: [],
  revenueBands: [],
  employeeRange: null,
  revenueRange: null,
};

const STRATEGY = {
  filter: EMPTY_FILTER,
  offLimits: [],
  searches: [
    {
      id: "search-1",
      name: "GCC industrials, 1000+ staff",
      filter: EMPTY_FILTER,
      createdAt: "2026-06-01T00:00:00Z",
    },
  ],
};

const REPORT_LEVELS = ["Board", "C-Suite", "N-1", "N-2", "N-3"];
const levelCounts = (board, cSuite, n1, n2) =>
  REPORT_LEVELS.map((level, i) => ({ level, count: [board, cSuite, n1, n2, 0][i] }));

const REPORT = {
  head: { universeCount: 128, executivesMapped: 41, truncated: false, generatedAt: "2026-09-08T07:40:00Z" },
  progress: {
    kickoff: "2026-07-21",
    targetDate: "2026-09-30",
    asOf: "2026-09-08",
    targetCompanies: 128,
    companiesCumulative: [0, 4, 9, 15, 19, 24, 26, 27],
    weekly: ["2026-07-27", "2026-08-03", "2026-08-10", "2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14"].map(
      (weekEnding, i) => ({ weekEnding, identified: [3, 7, 8, 6, 7, 5, 3, 2][i] }),
    ),
    daily: Array.from({ length: 50 }, (_, i) => (i % 7 < 5 ? 1 : 0)),
    daysSinceLastExecutive: 4,
  },
  market: {
    sectors: ["Oil & Energy", "Industrial Manufacturing", "Logistics"],
    levels: REPORT_LEVELS,
    cells: ["Oil & Energy", "Industrial Manufacturing", "Logistics"].flatMap((sector, s) =>
      REPORT_LEVELS.map((level, l) => ({ sector, level, count: [[1, 8, 6, 3, 0], [0, 6, 5, 2, 0], [0, 4, 3, 1, 0]][s][l] })),
    ),
    withoutSector: 2,
    withoutSeniority: 0,
    slices: [
      {
        sector: "Oil & Energy",
        level: "C-Suite",
        companies: ["ADNOC Distribution", "Masdar"],
        executives: [
          { id: "e1", fullName: "Yasmin El-Sayed", company: "ADNOC Distribution", status: "interested" },
          { id: "e2", fullName: "Omar Haddad", company: "Masdar", status: "engaged" },
        ],
      },
    ],
    hubs: [
      { country: "United Arab Emirates", count: 32, depth: levelCounts(1, 14, 11, 6), employers: ["ADNOC Distribution", "Masdar", "DP World"], interested: 10, gccNationals: 8, female: 10, recordedGender: 31, medianPackage: 2200000, point: { latitude: 23.4241, longitude: 53.8478 } },
      { country: "Saudi Arabia", count: 7, depth: levelCounts(0, 4, 3, 0), employers: ["ACWA Power"], interested: 2, gccNationals: 4, female: 2, recordedGender: 7, medianPackage: 1850000, point: { latitude: 23.8859, longitude: 45.0792 } },
    ],
    elsewhere: 2,
    unlocated: 0,
    companiesBySector: [
      { label: "Industrial Manufacturing & Engineering Services", count: 48 },
      { label: "Oil & Energy", count: 31 },
      { label: "Logistics & Supply Chain", count: 22 },
      { label: "Other", count: 27 },
    ],
  },
  remuneration: {
    currency: "AED",
    fixedBand: { low: 1200000, high: 1800000 },
    packageBand: { low: 1500000, high: 2300000 },
    disclosures: [
      { id: "e1", fullName: "Yasmin El-Sayed", company: "ADNOC Distribution", title: "CFO", country: "United Arab Emirates", nationality: "Arab expat, non-GCC", status: "interested", fixed: 1650000, totalPackage: 2100000, note: null },
      { id: "e2", fullName: "Omar Haddad", company: "Masdar", title: "VP Finance", country: "United Arab Emirates", nationality: "Arab expat, non-GCC", status: "engaged", fixed: 1400000, totalPackage: 1900000, note: "Within band." },
      { id: "e3", fullName: "Lina Said", company: "DP World", title: "Group CFO", country: "United Arab Emirates", nationality: "Emirati", status: "notInterested", fixed: 2200000, totalPackage: 3100000, note: "Above band." },
      { id: "e4", fullName: "Faisal Al-Amri", company: "ACWA Power", title: "CFO", country: "Saudi Arabia", nationality: "Saudi", status: "identified", fixed: 1500000, totalPackage: 2000000, note: null },
      { id: "e5", fullName: "Nour Khalil", company: "Masdar", title: "Finance Director", country: "United Arab Emirates", nationality: "Arab expat, non-GCC", status: "contacted", fixed: 1100000, totalPackage: 1400000, note: null },
    ],
    otherCurrency: 1,
  },
  diversity: {
    levels: REPORT_LEVELS,
    nationalities: [
      { nationality: "Emirati", gcc: true, byLevel: levelCounts(1, 5, 4, 2), unclassified: 0, total: 12 },
      { nationality: "Arab expat, non-GCC", gcc: false, byLevel: levelCounts(0, 4, 4, 2), unclassified: 0, total: 10 },
      { nationality: "Saudi", gcc: true, byLevel: levelCounts(0, 4, 2, 1), unclassified: 0, total: 7 },
      { nationality: "South Asian", gcc: false, byLevel: levelCounts(0, 3, 2, 1), unclassified: 0, total: 6 },
      { nationality: "Other", gcc: false, byLevel: levelCounts(0, 2, 2, 0), unclassified: 0, total: 4 },
    ],
    unknownNationality: 2,
    gccNationals: 19,
    genderByLevel: [
      { level: "Board", female: 0, male: 1, other: 0 },
      { level: "C-Suite", female: 5, male: 13, other: 0 },
      { level: "N-1", female: 4, male: 9, other: 1 },
      { level: "N-2", female: 3, male: 3, other: 0 },
      { level: "N-3", female: 0, male: 0, other: 0 },
    ],
    genderWithoutLevel: { female: 0, male: 0, other: 0 },
    genderUnrecorded: 2,
  },
};

const TRIAGE_COUNTS = { inUniverse: 128, shortlisted: 24, declined: 61 };

// Every third one is hand-typed, and a hand-typed company has no universe id to carry — which is
// also what decides whether its panel offers an Edit button.
const isManual = (i) => i % 3 === 0;

const TRIAGE_COMPANIES = Array.from({ length: 8 }, (_, i) => ({
  id: `triage-${i + 1}`,
  apolloAccountId: isManual(i) ? null : `apollo-${i + 1}`,
  status: "inUniverse",
  note: null,
  companyName: `Gulf Industrial Holdings Company ${i + 1}`,
  industry: "Industrial Manufacturing & Engineering Services",
  companyCountry: "United Arab Emirates",
  companyCity: "Abu Dhabi",
  numEmployees: 1000 + i * 37,
  annualRevenue: null,
  website: "https://gulf-industrial.example",
  companyLinkedinUrl: null,
  foundedYear: 1998,
  shortDescription: null,
  sourceUrl: null,
  logoUrl: null,
  source: isManual(i) ? "manual" : "strategy",
  addedAt: "2026-08-01T09:00:00.000Z",
}));

/**
 * Executives at the first three companies, so the grid's Executive/Title/Status columns are exercised
 * and the "same company, two rows" case is on screen. Company 1 carries two of them; companies 4-8
 * carry none, which is the "+ Add executive" slot.
 */
const CANDIDATES = [
  ["cand-1", "triage-1", "Yasmin El-Sayed", "VP Finance", "engaged"],
  ["cand-2", "triage-1", "Omar Haddad", "Chief Financial Officer", "interested"],
  ["cand-3", "triage-2", "Wei Ling Tan", "Group Chief Executive Officer", "contacted"],
  ["cand-4", "triage-3", "Stefan Lindqvist", "Managing Director", "identified"],
  // Nobody's employer in the universe: the row the In-universe stage appends after the companies.
  ["cand-5", null, "Nadia Rahman", "Group CFO", "identified"],
].map(([id, triageCompanyId, fullName, title, status]) => ({
  id,
  triageCompanyId,
  companyName: triageCompanyId ? "Gulf Industrial Holdings Company 1" : "An Unlisted Family Holding",
  fullName,
  title,
  seniority: "N-1",
  status,
  linkedinUrl: null,
  locationCountry: "United Arab Emirates",
  locationCity: "Dubai",
  nationality: null,
  yearsExperience: 18,
  summary: null,
  note: null,
  compensation: {
    currency: null, baseSalary: null, bonus: null, allowances: null,
    longTermIncentive: null, noticePeriod: null,
  },
  career: [],
  languages: [],
  education: [],
  skills: [],
  source: "manual",
  sourceUrl: null,
  customFields: {},
  addedAt: "2026-08-02T09:00:00.000Z",
  enrichedAt: null,
  contacts: { emails: [], phones: [], emailsLookedUpAt: null, phonesLookedUpAt: null, source: null },
}));

const POSITION = {
  details: {
    roleTitle: "Chief Operating Officer, Downstream Manufacturing",
    department: "Group Operations",
    location: "Abu Dhabi, United Arab Emirates",
    employmentType: "FULL_TIME_PERMANENT",
    seniority: "N_MINUS_1",
    responsibilities: [
      "Multi-site operational delivery",
      "Performance and productivity",
      "Operating-model design",
    ],
    narrative: "The group is consolidating four downstream plants under one operating leader.",
  },
  context: {
    mandateReason: "GROWTH_EXPANSION",
    businessDriver: "Consolidate four plants under one operating leader before the next capital phase.",
    strategicPriorities: [
      { name: "Operational excellence", selected: true },
      { name: "Capital discipline", selected: true },
      { name: "Talent development", selected: false },
    ],
    confidential: true,
    internalContext: "Succession for a retiring incumbent, confidential until Q4.",
  },
  reporting: {
    orgChart: [
      {
        nodeId: "11111111-1111-4111-8111-111111111111",
        parentNodeId: null,
        title: "Group Chief Executive Officer",
        name: "Hassan Al Marri",
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
      },
      {
        nodeId: "22222222-2222-4222-8222-222222222222",
        parentNodeId: "11111111-1111-4111-8111-111111111111",
        title: null,
        name: null,
        mandateSeat: true,
        canvasX: null,
        canvasY: null,
      },
      {
        nodeId: "33333333-3333-4333-8333-333333333333",
        parentNodeId: "22222222-2222-4222-8222-222222222222",
        title: "Plant Director, Ruwais",
        name: "Layla Nasser",
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
      },
      {
        nodeId: "44444444-4444-4444-8444-444444444444",
        parentNodeId: "22222222-2222-4222-8222-222222222222",
        title: "Head of Supply Chain",
        name: null,
        mandateSeat: false,
        canvasX: null,
        canvasY: null,
      },
    ],
    teamSize: "1,400 across the four plants",
    targetStart: "2026-11-30",
    noticeValue: 3,
    noticeUnit: "MONTHS",
  },
  compensation: {
    currency: "AED",
    salaryMin: 1200000,
    salaryMax: 1800000,
    baseSalaryMode: "ANNUAL",
    bonusValue: 35,
    bonusBasis: "PERCENT_OF_BASE",
    incentiveType: "LTIP_CASH",
    incentiveAmount: 900000,
    incentiveVesting: "Three-year performance share plan",
    benefits: [
      { name: "Housing allowance", amount: 30000, frequency: "MONTHLY" },
      { name: "Schooling", amount: 90000, frequency: "YEARLY" },
      { name: "Annual flights home", amount: null, frequency: "YEARLY" },
    ],
  },
  assessment: {
    criteria: [
      {
        text: "Ran a multi-site downstream manufacturing P&L above USD 500m",
        mode: "REQUIRED",
        fromBrief: true,
      },
      { text: "GCC operating experience", mode: "PREFERRED", fromBrief: true },
    ],
    technical: [
      { name: "Operational excellence", description: "Runs the operation on measures", weight: 40 },
      { name: "Capital projects", description: null, weight: 35 },
      { name: "Commercial acumen", description: null, weight: 25 },
    ],
    behavioural: [
      { name: "Executive presence", description: null, weight: 50 },
      { name: "Change leadership", description: null, weight: 50 },
    ],
  },
  publication: { publishedAt: null, publishedBy: null },
  document: null,
};

/** Path suffix -> payload, matched against the pathname. */
const WORKSPACE_DETAIL = {
  id: WORKSPACE.id,
  name: WORKSPACE.name,
  slug: WORKSPACE.slug,
  logoMark: WORKSPACE.logoMark,
  emailDomain: "meridian-partners.com",
  defaultRegion: "Middle East",
  defaultCurrency: "AED",
  plan: "PROFESSIONAL",
  memberCount: 4,
  createdAt: "2026-03-01T00:00:00Z",
};

const ROUTES = [
  ["/companies/facets", FACETS],
  [
    "/countries",
    {
      countries: [
        { code: "AE", name: "United Arab Emirates", spellings: ["uae"] },
        { code: "SA", name: "Saudi Arabia", spellings: ["ksa"] },
      ],
      markets: ["United Arab Emirates", "Saudi Arabia"],
    },
  ],
  ["/companies/search", { companies: [] }],
  ["/auth/refresh", { accessToken: "stub-access-token", expiresIn: 900 }],
  ["/auth/me", USER],
  ["/auth/providers", { google: false, linkedin: false }],
  ["/auth/sessions", []],
  ["/workspace", WORKSPACE_DETAIL],
  ["/members", MEMBERS],
  ["/invitations", []],
  ["/clients", CLIENTS],
  ["/projects", PROJECTS],
];

/**
 * @param pathname the request path
 * @param search   the query string, because two reads of the candidates endpoint differ only by it —
 *                 the people at this page's companies, and the ones mapped to no company at all.
 */
export function payloadFor(pathname, search = "") {
  if (pathname.endsWith("/auth/csrf")) return {};

  if (/\/projects\/[^/]+\/strategy\/companies/.test(pathname))
    return { companies: COMPANIES, totalCount: 71822, page: 0, size: 25 };
  if (/\/projects\/[^/]+\/strategy/.test(pathname)) return STRATEGY;
  if (/\/projects\/[^/]+\/candidates/.test(pathname)) {
    const unmapped = new URLSearchParams(search).get("unmapped") === "true";
    const answer = CANDIDATES.filter((c) => (c.triageCompanyId === null) === unmapped);
    return { candidates: answer, totalCount: answer.length, page: 0, size: 25 };
  }
  if (/\/projects\/[^/]+\/triage/.test(pathname))
    return {
      companies: TRIAGE_COMPANIES,
      totalCount: TRIAGE_COUNTS.inUniverse,
      page: 0,
      size: 25,
      counts: TRIAGE_COUNTS,
    };
  if (/\/projects\/[^/]+\/report/.test(pathname)) return REPORT;
  // The Companies grid asks only for the brief's package, to offer its currency to a new executive.
  if (/\/projects\/[^/]+\/position\/compensation$/.test(pathname)) return POSITION.compensation;
  if (/\/projects\/[^/]+\/position/.test(pathname)) return POSITION;
  if (/\/projects\/[^/]+$/.test(pathname)) {
    const id = pathname.split("/").pop();
    return PROJECTS.find((p) => p.id === id) ?? PROJECTS[0];
  }

  const match = ROUTES.find(([suffix]) => pathname.endsWith(suffix));
  return match ? match[1] : [];
}
