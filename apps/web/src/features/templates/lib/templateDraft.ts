import type {
  BaseSalaryMode,
  BenefitFrequency,
  BonusBasis,
  Criterion,
  EmploymentType,
  IncentiveType,
  NoticeUnit,
  PositionDiscipline,
  PositionSeniority,
} from "../../position/api/types";
import { forWire, identify, type IdentifiedCompetency } from "../../position/lib/competencyRows";
import type { CompetencyPanelKind, TemplateDetail, TemplateWriteRequest } from "../api/types";

export interface DraftBenefit {
  /** Client-side only: keys the row, so removing one never hands its inputs to the next. */
  id: string;
  name: string;
  frequency: BenefitFrequency;
}

/**
 * A template while the editor holds it: free text as strings (blank means unset), criteria in the
 * shape the brief's own `CriteriaCard` takes, and the competencies split into the two panels the
 * screen draws. {@link requestOf} rebuilds the wire shape.
 */
export interface TemplateDraft {
  title: string;
  discipline: PositionDiscipline;
  seniority: PositionSeniority;
  summary: string;
  keywords: string[];
  department: string;
  employmentType: EmploymentType | null;
  narrative: string;
  responsibilities: string[];
  strategicPriorities: string[];
  reportsTo: string;
  directReports: string[];
  noticeValue: number | null;
  noticeUnit: NoticeUnit | null;
  currency: string;
  baseSalaryMode: BaseSalaryMode;
  bonusValue: number | null;
  bonusBasis: BonusBasis | null;
  incentiveType: IncentiveType | null;
  incentiveVesting: string;
  benefits: DraftBenefit[];
  criteria: Criterion[];
  technical: IdentifiedCompetency[];
  behavioural: IdentifiedCompetency[];
}

export function draftOf(detail: TemplateDetail): TemplateDraft {
  const { body } = detail;
  const panel = (kind: CompetencyPanelKind) =>
    identify(
      body.competencies
        .filter((competency) => competency.panel === kind)
        .map(({ name, description, weight }) => ({ name, description, weight })),
    );
  return {
    title: detail.title,
    discipline: detail.discipline,
    seniority: detail.seniority,
    summary: detail.summary ?? "",
    keywords: detail.keywords,
    department: body.department ?? "",
    employmentType: body.employmentType,
    narrative: body.narrative ?? "",
    responsibilities: body.responsibilities,
    strategicPriorities: body.strategicPriorities,
    reportsTo: body.reportsTo ?? "",
    directReports: body.directReports,
    noticeValue: body.noticeValue,
    noticeUnit: body.noticeUnit,
    currency: body.currency,
    baseSalaryMode: body.baseSalaryMode,
    bonusValue: body.bonusValue,
    bonusBasis: body.bonusBasis,
    incentiveType: body.incentiveType,
    incentiveVesting: body.incentiveVesting ?? "",
    benefits: body.benefits.map((benefit) => ({
      id: crypto.randomUUID(),
      name: benefit.name,
      frequency: benefit.frequency ?? "MONTHLY",
    })),
    criteria: body.criteria.map((criterion) => ({
      text: criterion.text,
      mode: criterion.mode ?? "REQUIRED",
      fromBrief: false,
    })),
    technical: panel("TECHNICAL"),
    behavioural: panel("BEHAVIOURAL"),
  };
}

export function blankDraft(): TemplateDraft {
  return {
    title: "",
    discipline: "EXECUTIVE",
    seniority: "C_SUITE",
    summary: "",
    keywords: [],
    department: "",
    employmentType: "FULL_TIME_PERMANENT",
    narrative: "",
    responsibilities: [],
    strategicPriorities: [],
    reportsTo: "",
    directReports: [],
    noticeValue: 3,
    noticeUnit: "MONTHS",
    currency: "USD",
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveVesting: "",
    benefits: [],
    criteria: [],
    technical: [],
    behavioural: [],
  };
}

export function requestOf(draft: TemplateDraft, version: number | null): TemplateWriteRequest {
  const orNull = (text: string) => text.trim() || null;
  const inPanel = (panel: CompetencyPanelKind, rows: IdentifiedCompetency[]) =>
    forWire(rows).map((competency) => ({ panel, ...competency }));
  return {
    title: draft.title.trim(),
    discipline: draft.discipline,
    seniority: draft.seniority,
    summary: orNull(draft.summary),
    keywords: draft.keywords,
    body: {
      department: orNull(draft.department),
      employmentType: draft.employmentType,
      narrative: orNull(draft.narrative),
      responsibilities: draft.responsibilities,
      reportsTo: orNull(draft.reportsTo),
      directReports: draft.directReports,
      strategicPriorities: draft.strategicPriorities,
      noticeValue: draft.noticeValue,
      noticeUnit: draft.noticeUnit,
      currency: draft.currency,
      baseSalaryMode: draft.baseSalaryMode,
      bonusValue: draft.bonusValue,
      bonusBasis: draft.bonusBasis,
      incentiveType: draft.incentiveType,
      incentiveVesting: orNull(draft.incentiveVesting),
      benefits: draft.benefits.map(({ id: _id, ...benefit }) => benefit),
      criteria: draft.criteria.map(({ text, mode }) => ({ text, mode })),
      competencies: [...inPanel("TECHNICAL", draft.technical), ...inPanel("BEHAVIOURAL", draft.behavioural)],
    },
    version,
  };
}

export function panelTotal(rows: IdentifiedCompetency[]): number {
  return rows.reduce((sum, row) => sum + row.weight, 0);
}

/** What stops a save before the server is asked. The server checks the rest, and the same again. */
export function draftProblems(draft: TemplateDraft): string[] {
  const problems: string[] = [];
  if (!draft.title.trim()) problems.push("Give the template a title.");
  if (draft.technical.length > 0 && panelTotal(draft.technical) !== 100) {
    problems.push("Technical competency weights must total 100.");
  }
  if (draft.behavioural.length > 0 && panelTotal(draft.behavioural) !== 100) {
    problems.push("Behavioural competency weights must total 100.");
  }
  if ([...draft.technical, ...draft.behavioural].some((row) => !row.name.trim())) {
    problems.push("Name every competency.");
  }
  if (draft.criteria.some((criterion) => !criterion.text.trim())) problems.push("Every criterion needs its text.");
  if (draft.benefits.some((benefit) => !benefit.name.trim())) problems.push("Name every benefit.");
  return problems;
}
