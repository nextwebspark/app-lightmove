import type {
  BaseSalaryMode,
  BenefitFrequency,
  BonusBasis,
  CriterionMode,
  EmploymentType,
  IncentiveType,
  MandateReason,
  NoticeUnit,
  PositionDiscipline,
  PositionSeniority,
} from "../../position/api/types";

/** The role-template management contract, hand-mirrored from the records in the positiontemplate package. */

/** The library a super admin edits, or the caller's own workspace's templates. */
export type TemplateScope = "library" | "workspace";

/** Where a template in a workspace's list comes from, and whether the firm has changed it. */
export type TemplateOrigin = "LIBRARY" | "CUSTOMISED" | "OWN" | "HIDDEN";

export type CompetencyPanelKind = "TECHNICAL" | "BEHAVIOURAL";

export interface TemplateBenefit {
  name: string;
  frequency: BenefitFrequency | null;
}

export interface TemplateCriterion {
  text: string;
  mode: CriterionMode | null;
}

export interface TemplateCompetency {
  panel: CompetencyPanelKind;
  name: string;
  description: string | null;
  weight: number;
}

/** One seat of a template's org chart: a title, never a person. The role's own seat has no title. */
export interface TemplateSeat {
  id: string;
  parentId: string | null;
  title: string | null;
  mandateSeat: boolean;
}

/**
 * What a template drafts into a new brief, in the brief's own step order. Never a role title,
 * location or salary band — those are each mandate's.
 */
export interface TemplateBody {
  employmentType: EmploymentType | null;
  /** Null leaves each mandate's own reason for hire alone. */
  mandateReason: MandateReason | null;
  /** Null leaves each mandate's own confidentiality alone. */
  confidential: boolean | null;
  noticeValue: number | null;
  noticeUnit: NoticeUnit | null;
  responsibilities: string[];
  narrative: string | null;
  orgChart: TemplateSeat[];
  currency: string;
  baseSalaryMode: BaseSalaryMode;
  bonusValue: number | null;
  bonusBasis: BonusBasis | null;
  incentiveType: IncentiveType | null;
  incentiveVesting: string | null;
  benefits: TemplateBenefit[];
  criteria: TemplateCriterion[];
  competencies: TemplateCompetency[];
  technicalShare: number;
}

export interface TemplateOverview {
  code: string;
  title: string;
  discipline: PositionDiscipline;
  seniority: PositionSeniority;
  summary: string | null;
  /** Null in the library scope, where every row is the library. */
  origin: TemplateOrigin | null;
  active: boolean;
  /** The template an unrecognised role title is drafted from — never archived or hidden. */
  fallback: boolean;
  libraryChangedSinceCustomised: boolean;
  keywords: string[];
  /** Library scope only: how many workspaces keep their own copy, and so will not see an edit. */
  customisedByWorkspaces: number | null;
  revisedAt: string;
  /** Null for a library template seen from a workspace. */
  revisedByName: string | null;
}

export interface TemplateDetail extends TemplateOverview {
  body: TemplateBody;
  /** Sent back on save. For a library template opened from a workspace, the library row's. */
  version: number;
}

export interface TemplateWriteRequest {
  title: string;
  discipline: PositionDiscipline;
  seniority: PositionSeniority;
  summary: string | null;
  keywords: string[];
  body: TemplateBody;
  /** The version the editor opened; ignored when creating. */
  version: number | null;
}

export type TemplateImportAction = "CREATE" | "UPDATE" | "CUSTOMISE" | "UNCHANGED" | "INVALID";

export interface TemplateImportProblem {
  /** A path inside the template, e.g. `body.competencies.technical`. */
  field: string;
  message: string;
}

export interface TemplateImportRow {
  code: string | null;
  title: string;
  action: TemplateImportAction;
  problems: TemplateImportProblem[];
}

/** An import's plan (preview) or its outcome (commit), one row per template, in file order. */
export interface TemplateImportResult {
  committed: boolean;
  rows: TemplateImportRow[];
}
