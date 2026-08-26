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
  countries: [
    { value: "United Arab Emirates", label: "United Arab Emirates" },
    { value: "Saudi Arabia", label: "Saudi Arabia" },
    { value: "Qatar", label: "Qatar" },
  ],
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

const REPORT = {
  universeCount: 128,
  offLimitsCompanies: 4,
  sectorsInScope: 6,
  marketsInScope: 3,
  sectors: [
    { label: "Industrial Manufacturing & Engineering Services", count: 48 },
    { label: "Oil & Energy", count: 31 },
    { label: "Logistics & Supply Chain", count: 22 },
  ],
  countries: [
    { label: "United Arab Emirates", count: 61 },
    { label: "Saudi Arabia", count: 44 },
  ],
  cities: [
    { label: "Abu Dhabi", count: 33 },
    { label: "Dubai", count: 28 },
  ],
  mandateBand: { min: 1200000, max: 1800000, currency: "AED" },
  caveats: { revenueBandExcludesUnknown: true },
};

const TRIAGE_COUNTS = { inUniverse: 128, shortlisted: 24, declined: 61 };

const TRIAGE_COMPANIES = Array.from({ length: 8 }, (_, i) => ({
  id: `triage-${i + 1}`,
  apolloAccountId: `apollo-${i + 1}`,
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
  source: i % 3 === 0 ? "manual" : "strategy",
  addedAt: "2026-08-01T09:00:00.000Z",
}));

const POSITION = {
  mandateReason: "GROWTH",
  internalContext: "Succession for a retiring incumbent, confidential until Q4.",
  narrative: "The group is consolidating four downstream plants under one operating leader.",
  reportsTo: "Group Chief Executive Officer",
  directReports: 7,
  teamSize: 1400,
  location: "Abu Dhabi, United Arab Emirates",
  employmentType: "FULL_TIME",
  startTarget: "2026-11-30",
  salaryMin: 1200000,
  salaryMax: 1800000,
  currency: "AED",
  noticeValue: 3,
  noticeUnit: "MONTHS",
  bonusTargetPct: 35,
  ltip: "Three-year performance share plan",
  benefits: ["Housing allowance", "Schooling", "Annual flights home", "Private medical"],
  confidential: true,
  criteria: [
    { text: "Ran a multi-site downstream manufacturing P&L above USD 500m", mode: "MUST", fromBrief: true },
    { text: "GCC operating experience", mode: "SHOULD", fromBrief: true },
  ],
  technical: [
    { name: "Operational excellence", weight: 40 },
    { name: "Capital projects", weight: 35 },
    { name: "Commercial acumen", weight: 25 },
  ],
  behavioural: [
    { name: "Executive presence", weight: 50 },
    { name: "Change leadership", weight: 50 },
  ],
  locked: false,
  lockedAt: null,
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

export function payloadFor(pathname) {
  if (pathname.endsWith("/auth/csrf")) return {};

  if (/\/projects\/[^/]+\/strategy\/companies/.test(pathname))
    return { companies: COMPANIES, totalCount: 71822, page: 0, size: 25 };
  if (/\/projects\/[^/]+\/strategy/.test(pathname)) return STRATEGY;
  if (/\/projects\/[^/]+\/triage/.test(pathname))
    return {
      companies: TRIAGE_COMPANIES,
      totalCount: TRIAGE_COUNTS.inUniverse,
      page: 0,
      size: 25,
      counts: TRIAGE_COUNTS,
    };
  if (/\/projects\/[^/]+\/report/.test(pathname)) return REPORT;
  if (/\/projects\/[^/]+\/position/.test(pathname)) return POSITION;
  if (/\/projects\/[^/]+$/.test(pathname)) {
    const id = pathname.split("/").pop();
    return PROJECTS.find((p) => p.id === id) ?? PROJECTS[0];
  }

  const match = ROUTES.find(([suffix]) => pathname.endsWith(suffix));
  return match ? match[1] : [];
}
