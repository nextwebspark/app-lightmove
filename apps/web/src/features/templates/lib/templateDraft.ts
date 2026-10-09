import { DEFAULT_CURRENCY } from "../../../lib/currencies";
import type {
  BaseSalaryMode,
  BenefitFrequency,
  BonusBasis,
  Criterion,
  EmploymentType,
  IncentiveType,
  MandateReason,
  NoticeUnit,
  OrgNode,
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
 * A template while the editor holds it: free text as strings (blank means unset), the chart and the
 * criteria in the shapes the brief's own `OrgChartCanvas` and `CriteriaCard` take, and the
 * competencies split into the two panels the screen draws. {@link requestOf} rebuilds the wire shape.
 */
export interface TemplateDraft {
  title: string;
  discipline: PositionDiscipline;
  seniority: PositionSeniority;
  summary: string;
  keywords: string[];
  employmentType: EmploymentType | null;
  mandateReason: MandateReason | null;
  confidential: boolean | null;
  noticeValue: number | null;
  noticeUnit: NoticeUnit | null;
  responsibilities: string[];
  narrative: string;
  orgChart: OrgNode[];
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
  technicalShare: number;
}

/** The id the server gives a chart's own seat when it has to make one. */
const ROLE_SEAT_ID = "role";

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
    employmentType: body.employmentType,
    mandateReason: body.mandateReason,
    confidential: body.confidential,
    noticeValue: body.noticeValue,
    noticeUnit: body.noticeUnit,
    responsibilities: body.responsibilities,
    narrative: body.narrative ?? "",
    orgChart: body.orgChart.map((seat) => seatNode(seat.id, seat.parentId, seat.title, seat.mandateSeat)),
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
      source: "MANUAL",
    })),
    technical: panel("TECHNICAL"),
    behavioural: panel("BEHAVIOURAL"),
    technicalShare: body.technicalShare,
  };
}

export function blankDraft(): TemplateDraft {
  return {
    title: "",
    discipline: "EXECUTIVE",
    seniority: "C_SUITE",
    summary: "",
    keywords: [],
    employmentType: "FULL_TIME_PERMANENT",
    mandateReason: null,
    confidential: null,
    noticeValue: 3,
    noticeUnit: "MONTHS",
    responsibilities: [],
    narrative: "",
    orgChart: [seatNode(ROLE_SEAT_ID, null, null, true)],
    currency: DEFAULT_CURRENCY,
    baseSalaryMode: "ANNUAL",
    bonusValue: null,
    bonusBasis: null,
    incentiveType: null,
    incentiveVesting: "",
    benefits: [],
    criteria: [],
    technical: [],
    behavioural: [],
    technicalShare: 50,
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
      employmentType: draft.employmentType,
      mandateReason: draft.mandateReason,
      confidential: draft.confidential,
      noticeValue: draft.noticeValue,
      noticeUnit: draft.noticeUnit,
      responsibilities: draft.responsibilities,
      narrative: orNull(draft.narrative),
      orgChart: draft.orgChart.map((node) => ({
        id: node.nodeId,
        parentId: node.parentNodeId,
        title: node.mandateSeat ? null : (node.title?.trim() || null),
        mandateSeat: node.mandateSeat,
      })),
      currency: draft.currency,
      baseSalaryMode: draft.baseSalaryMode,
      bonusValue: draft.bonusValue,
      bonusBasis: draft.bonusBasis,
      incentiveType: draft.incentiveType,
      incentiveVesting: orNull(draft.incentiveVesting),
      benefits: draft.benefits.map(({ id: _id, ...benefit }) => benefit),
      criteria: draft.criteria.map(({ text, mode }) => ({ text, mode })),
      competencies: [...inPanel("TECHNICAL", draft.technical), ...inPanel("BEHAVIOURAL", draft.behavioural)],
      technicalShare: draft.technicalShare,
    },
    version,
  };
}

/** A seat as the brief's canvas draws it. Names and positions stay empty: a template never keeps them. */
function seatNode(nodeId: string, parentNodeId: string | null, title: string | null, mandateSeat: boolean): OrgNode {
  return { nodeId, parentNodeId, title, name: null, mandateSeat, canvasX: null, canvasY: null };
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
  if (draft.orgChart.some((node) => !node.mandateSeat && !node.title?.trim())) {
    problems.push("Give every seat on the chart a title.");
  }
  return problems;
}
