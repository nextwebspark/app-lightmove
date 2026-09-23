import type { NoticeUnit } from "../../../lib/noticePeriod";
import { pairOfNoticePeriod } from "../../../lib/noticePeriod";
import { SENIORITY_TIERS } from "../../../lib/seniority";
import type {
  Competency,
  Criterion,
  FieldSource,
  MandateContext,
  PositionDetails,
  PositionExtraction,
  ProposalConfidence,
  ProposedField,
  ReportingStructure,
} from "../api/types";
import { EMPLOYMENT_TYPE_LABELS } from "./labels";
import { mergeReportingProposals, type ChartMergeBlock } from "./orgChart";
import type { StepKey } from "./steps";

/**
 * Folds a "Read from document" reading silently into the brief: what the old review-then-accept panel
 * did one click at a time, this does in one pass, marking what it filled instead of asking somebody to
 * approve each row. See epic #393 for the product reasoning and #396 for this library's contract.
 *
 * Every function here is pure — a snapshot in, a snapshot out — so the wiring (#397) can call it from
 * whatever autosave shape the page happens to hold, and so the merge, weight and undo rules can be
 * proven with plain data rather than a rendered page.
 */

const EMPLOYMENT_TYPES: readonly string[] = Object.keys(EMPLOYMENT_TYPE_LABELS);

export function isEmploymentType(value: string): value is NonNullable<PositionDetails["employmentType"]> {
  return EMPLOYMENT_TYPES.includes(value);
}

export function isSeniority(value: string): value is NonNullable<PositionDetails["seniority"]> {
  return (SENIORITY_TIERS as readonly string[]).includes(value);
}

/** Mirrors the backend's own per-brief ceilings — `PutPositionDetailsRequest`, `PutMandateContextRequest`,
 *  `PutCriteriaRequest`, `PutCompetenciesRequest` — so a fill can never propose past what a PUT accepts. */
export const RESPONSIBILITY_MAX_COUNT = 20;
export const PRIORITY_MAX_COUNT = 20;
export const CRITERIA_MAX_COUNT = 30;
export const COMPETENCY_MAX_COUNT_PER_PANEL = 10;

/**
 * The four sections a document reading is extracted for — one `/extract/*` call each, unchanged by the
 * #442 rebuild's five-screen rail. Compensation is never read (#395); review has nothing of its own to
 * read.
 */
export type ExtractionSection = "details" | "context" | "reporting" | "assessment";

/** One settled section reading per extracted section — missing when a section's own call failed or was
 *  never run, exactly as `Promise.allSettled` leaves it. */
export type FillResults = Partial<Record<ExtractionSection, PositionExtraction>>;

/**
 * The six drafts a fill can touch, in the shape the wizard's own autosave state already keeps them —
 * `criteria` and the two competency panels are their own snapshot fields, not one `assessment` object,
 * because `putCriteria` and `putCompetencies` are separate writes and the Assessment Criteria screen's
 * two channels must be schedulable independently of each other.
 */
export interface PositionSnapshot {
  details: PositionDetails;
  context: MandateContext;
  reporting: ReportingStructure;
  criteria: Criterion[];
  technical: Competency[];
  behavioural: Competency[];
  technicalShare: number;
}

/** What a scalar held immediately before this fill touched it — enough to undo it, and enough to say
 *  where the new value came from. */
export interface ScalarReceipt {
  previousValue: unknown;
  previousSource: FieldSource | undefined;
  confidence: ProposalConfidence;
  snippet: string | null;
}

/** What a fill did to one repeatable list: the rows it dropped (restored if every row it added is
 *  later undone) and, per row it added, the confidence/snippet a hover reads. */
export interface ListReceipt {
  dropped: unknown[];
  appended: Record<string, { confidence: ProposalConfidence; snippet: string | null }>;
}

export interface StepReceipt {
  fileName: string;
  scalars: Partial<Record<string, ScalarReceipt>>;
  lists: Partial<Record<string, ListReceipt>>;
}

/**
 * One receipt per **screen**, not per extracted section: the Role Brief's receipt covers the details
 * and context scalars/lists a fill touched, plus the reporting notice pair — the three writes that one
 * screen's "Undo" strip must be able to reverse together, since all three autosave from the same page.
 */
export type Receipts = Partial<Record<StepKey, StepReceipt>>;

export type SkipReason = "manual" | "invalid" | ChartMergeBlock;

export interface SkippedField {
  step: ExtractionSection;
  fieldKey: string;
  reason: SkipReason;
}

export interface FillOutcome {
  next: PositionSnapshot;
  /** Which screens a caller must schedule a save for — the Role Brief implies all three of its own
   *  channels (details, context, reporting), since notice lives in the reporting draft. */
  changed: Set<StepKey>;
  receipts: Receipts;
  skipped: SkippedField[];
}

/** How many fields one screen's receipt filled — the rail badge's `N filled` and the strip's own count. */
export function fieldCountOf(receipt: StepReceipt | undefined): number {
  if (!receipt) return 0;
  const scalarCount = Object.keys(receipt.scalars).length;
  const listCount = Object.values(receipt.lists).reduce(
    (sum, list) => sum + (list ? Object.keys(list.appended).length : 0),
    0,
  );
  return scalarCount + listCount;
}

/**
 * Reads a whole document reading into the brief: every scalar and repeatable list a section proposed,
 * folded source-aware over the current drafts, plus the reporting section's org-chart merge. Nothing
 * here schedules a save or renders anything — the caller (#397) does that with `changed`.
 */
export function fillBrief(snapshot: PositionSnapshot, results: FillResults, fileName: string): FillOutcome {
  const changed = new Set<StepKey>();
  const receipts: Receipts = {};
  const skipped: SkippedField[] = [];

  // --- Role Brief screen: details, mandate context, and the reporting structure's notice pair. ---
  const detailsFill = fillDetails(snapshot.details, results.details, skipped);
  const contextFill = fillContext(snapshot.context, results.context, skipped);

  const reportingFields = results.reporting?.fields ?? [];
  const noticeField = reportingFields.find((field) => field.fieldKey === "noticePeriod");
  const noticeFill = fillNoticePeriod(snapshot.reporting, noticeField);
  if (noticeFill.skipped) {
    skipped.push({ step: "reporting", fieldKey: "noticePeriod", reason: noticeFill.skipped });
  }

  const briefChanged = detailsFill.changed || contextFill.changed || noticeFill.changed;
  if (briefChanged) {
    changed.add("brief");
    receipts.brief = {
      fileName,
      scalars: {
        ...detailsFill.scalars,
        ...contextFill.scalars,
        ...(noticeFill.changed ? { noticePeriod: noticeFill.receipt } : {}),
      },
      lists: { ...detailsFill.lists, ...contextFill.lists },
    };
  }

  // --- Reporting screen: team size and the org chart. Notice already folded in above. ---
  const teamSizeField = reportingFields.find((field) => field.fieldKey === "teamSize");
  const teamSizeFill = fillScalar(noticeFill.draft, teamSizeField, TEAM_SIZE_LENS);
  if (teamSizeFill.skipped) {
    skipped.push({ step: "reporting", fieldKey: "teamSize", reason: teamSizeFill.skipped });
  }

  let reporting = teamSizeFill.draft;
  let orgChartChanged = false;
  const reportsTo =
    reportingFields.find((field) => field.fieldKey === "reportsToTitle" && field.origin === "document")
      ?.value ?? null;
  const directReports = reportingFields
    .filter((field) => field.fieldKey === "directReportTitle" && field.origin === "document")
    .map((field) => field.value);
  if (reportsTo || directReports.length > 0) {
    const merged = mergeReportingProposals(reporting.orgChart, reportsTo, directReports);
    if (merged.chart !== reporting.orgChart) {
      orgChartChanged = true;
      reporting = { ...reporting, orgChart: merged.chart };
    }
    // A chart at/near its seat cap can decline the proposed manager, the proposed direct reports, or
    // both — "mark what filled it" applies here too, so a caller can say a document read didn't fully
    // land rather than reporting `changed` alone as a silent success.
    if (merged.blocked) {
      skipped.push({ step: "reporting", fieldKey: "orgChart", reason: merged.blocked });
    }
  }

  const reportingChanged = teamSizeFill.changed || orgChartChanged;
  if (reportingChanged) {
    changed.add("reporting");
    receipts.reporting = {
      fileName,
      scalars: teamSizeFill.changed ? { teamSize: teamSizeFill.receipt } : {},
      lists: {},
    };
  }

  // --- Assessment Criteria screen: the criteria list and both competency panels. ---
  const assessmentFields = results.assessment?.fields ?? [];

  const criteriaFill = mergeProposedList(
    snapshot.criteria,
    assessmentFields,
    ["requiredCriterion", "preferredCriterion"],
    (field): Criterion => ({
      text: field.value,
      mode: field.fieldKey === "requiredCriterion" ? "REQUIRED" : "PREFERRED",
      source: "DOCUMENT",
    }),
    (item) => item.text,
    CRITERIA_MAX_COUNT,
  );

  const technicalFill = mergeProposedList(
    snapshot.technical,
    assessmentFields,
    ["technicalCompetency"],
    (field): Competency => ({ ...competencyFrom(field.value), source: "DOCUMENT" }),
    (item) => item.name,
    COMPETENCY_MAX_COUNT_PER_PANEL,
  );

  const behaviouralFill = mergeProposedList(
    snapshot.behavioural,
    assessmentFields,
    ["behaviouralCompetency"],
    (field): Competency => ({ ...competencyFrom(field.value), source: "DOCUMENT" }),
    (item) => item.name,
    COMPETENCY_MAX_COUNT_PER_PANEL,
  );

  const assessmentChanged = Boolean(criteriaFill.receipt || technicalFill.receipt || behaviouralFill.receipt);
  const technical = technicalFill.receipt ? balancePanelWeights(technicalFill.merged) : technicalFill.merged;
  const behavioural = behaviouralFill.receipt
    ? balancePanelWeights(behaviouralFill.merged)
    : behaviouralFill.merged;
  if (assessmentChanged) {
    changed.add("assessment");
    receipts.assessment = {
      fileName,
      scalars: {},
      lists: {
        ...(criteriaFill.receipt ? { criteria: criteriaFill.receipt } : {}),
        ...(technicalFill.receipt ? { technical: technicalFill.receipt } : {}),
        ...(behaviouralFill.receipt ? { behavioural: behaviouralFill.receipt } : {}),
      },
    };
  }

  return {
    next: {
      ...snapshot,
      details: detailsFill.draft,
      context: contextFill.draft,
      reporting,
      criteria: criteriaFill.merged,
      technical,
      behavioural,
    },
    changed,
    receipts,
    skipped,
  };
}

// ---------------------------------------------------------------------------------------------------
// Scalars — one lens per field, so "check MANUAL, stamp DOCUMENT, remember the previous" is written once.
// ---------------------------------------------------------------------------------------------------

interface ScalarLens<D, V> {
  key: string;
  get: (draft: D) => V;
  set: (draft: D, value: V) => D;
  /** `undefined` means the raw value does not belong to this field — dropped, never written. */
  parse: (raw: string) => V | undefined;
}

interface ScalarFillResult<D> {
  draft: D;
  changed: boolean;
  receipt?: ScalarReceipt;
  skipped?: SkipReason;
}

function fillScalar<D extends { fieldSources: Record<string, FieldSource> }, V>(
  draft: D,
  field: ProposedField | undefined,
  lens: ScalarLens<D, V>,
): ScalarFillResult<D> {
  if (!field || field.origin !== "document") return { draft, changed: false };
  const currentSource = draft.fieldSources[lens.key];
  if (currentSource === "MANUAL") return { draft, changed: false, skipped: "manual" };
  const value = lens.parse(field.value);
  if (value === undefined) return { draft, changed: false, skipped: "invalid" };

  const receipt: ScalarReceipt = {
    previousValue: lens.get(draft),
    previousSource: currentSource,
    confidence: field.confidence,
    snippet: field.snippet,
  };
  const updated = lens.set(draft, value);
  return {
    draft: { ...updated, fieldSources: { ...updated.fieldSources, [lens.key]: "DOCUMENT" } },
    changed: true,
    receipt,
  };
}

const DEPARTMENT_LENS: ScalarLens<PositionDetails, string | null> = {
  key: "department",
  get: (d) => d.department,
  set: (d, value) => ({ ...d, department: value }),
  parse: (raw) => raw || null,
};
// A document writes where a role sits on one line; `LocationLine` splits it server-side into these two
// proposals, so each half fills, marks and undoes on its own. The country arrives already spelled as
// the catalog spells it, which is what the picker beside it reads.
const LOCATION_CITY_LENS: ScalarLens<PositionDetails, string | null> = {
  key: "locationCity",
  get: (d) => d.locationCity,
  set: (d, value) => ({ ...d, locationCity: value }),
  parse: (raw) => raw || null,
};
const LOCATION_COUNTRY_LENS: ScalarLens<PositionDetails, string | null> = {
  key: "locationCountry",
  get: (d) => d.locationCountry,
  set: (d, value) => ({ ...d, locationCountry: value }),
  parse: (raw) => raw || null,
};
const EMPLOYMENT_TYPE_LENS: ScalarLens<PositionDetails, PositionDetails["employmentType"]> = {
  key: "employmentType",
  get: (d) => d.employmentType,
  set: (d, value) => ({ ...d, employmentType: value }),
  parse: (raw) => (isEmploymentType(raw) ? raw : undefined),
};
const SENIORITY_LENS: ScalarLens<PositionDetails, PositionDetails["seniority"]> = {
  key: "seniority",
  get: (d) => d.seniority,
  set: (d, value) => ({ ...d, seniority: value }),
  parse: (raw) => (isSeniority(raw) ? raw : undefined),
};
const NARRATIVE_LENS: ScalarLens<PositionDetails, string | null> = {
  key: "narrative",
  get: (d) => d.narrative,
  set: (d, value) => ({ ...d, narrative: value }),
  parse: (raw) => raw || null,
};

const MANDATE_REASON_LENS: ScalarLens<MandateContext, MandateContext["mandateReason"]> = {
  key: "mandateReason",
  get: (c) => c.mandateReason,
  // The proposer only ever emits one of the five reasons the model was given — nothing here narrows it
  // further, the same trust `patchForContext` gave it before this library existed.
  set: (c, value) => ({ ...c, mandateReason: value }),
  parse: (raw) => raw as MandateContext["mandateReason"],
};
const BUSINESS_DRIVER_LENS: ScalarLens<MandateContext, string | null> = {
  key: "businessDriver",
  get: (c) => c.businessDriver,
  set: (c, value) => ({ ...c, businessDriver: value }),
  parse: (raw) => raw || null,
};

const TEAM_SIZE_LENS: ScalarLens<ReportingStructure, string | null> = {
  key: "teamSize",
  get: (r) => r.teamSize,
  set: (r, value) => ({ ...r, teamSize: value }),
  parse: (raw) => raw || null,
};

/**
 * The reporting section's one composite scalar: a `noticePeriod` proposal is always one of the five
 * vocabulary labels (`PositionReportingProposer` folds the model's count and unit into one before this
 * ever sees it), so `pairOfNoticePeriod` should never fail here — but this is a pure function fed
 * whatever a caller hands it, so an unrecognised label or a pair that resolves to a non-finite or
 * negative count is dropped rather than written. `Number(value)` with no such guard is the fold that
 * shipped the NaN bug this replaces.
 */
function fillNoticePeriod(
  reporting: ReportingStructure,
  field: ProposedField | undefined,
): ScalarFillResult<ReportingStructure> {
  if (!field || field.origin !== "document") return { draft: reporting, changed: false };
  const currentSource = reporting.fieldSources.noticeValue;
  if (currentSource === "MANUAL") return { draft: reporting, changed: false, skipped: "manual" };

  const pair = pairOfNoticePeriod(field.value);
  if (!pair || !Number.isFinite(pair.noticeValue) || pair.noticeValue < 0) {
    return { draft: reporting, changed: false, skipped: "invalid" };
  }

  const receipt: ScalarReceipt = {
    previousValue: { noticeValue: reporting.noticeValue, noticeUnit: reporting.noticeUnit },
    previousSource: currentSource,
    confidence: field.confidence,
    snippet: field.snippet,
  };
  return {
    draft: {
      ...reporting,
      noticeValue: pair.noticeValue,
      noticeUnit: pair.noticeUnit,
      fieldSources: { ...reporting.fieldSources, noticeValue: "DOCUMENT", noticeUnit: "DOCUMENT" },
    },
    changed: true,
    receipt,
  };
}

// ---------------------------------------------------------------------------------------------------
// Repeatable lists — responsibilities, priorities, criteria, both competency panels all merge the same
// way: keep what a person typed, drop everything else the brief already held, append what is new.
// ---------------------------------------------------------------------------------------------------

interface Sourced {
  source?: FieldSource;
}

function isManual(item: Sourced): boolean {
  return (item.source ?? "MANUAL") === "MANUAL";
}

interface ListMergeResult<T> {
  merged: T[];
  dropped: T[];
  appended: T[];
}

/**
 * `kept` is every `MANUAL` row, in place. Everything else — `TEMPLATE` rows and a previous reading's
 * `DOCUMENT` rows — is dropped, since a fresh read replaces the last one rather than piling onto it.
 * `proposed` rows are appended in order, skipping a case-insensitive duplicate of anything already kept
 * and stopping at `cap` — which counts `kept` first, so a brief already near its ceiling still cannot
 * take everything a reading offers.
 */
function mergeList<T extends Sourced>(
  existing: T[],
  proposed: T[],
  keyOf: (item: T) => string,
  cap: number,
): ListMergeResult<T> {
  const kept = existing.filter(isManual);
  const dropped = existing.filter((item) => !isManual(item));
  const keptKeys = new Set(kept.map((item) => keyOf(item).trim().toLowerCase()));
  const appended: T[] = [];
  let total = kept.length;

  for (const item of proposed) {
    const key = keyOf(item).trim().toLowerCase();
    if (!key || keptKeys.has(key) || total >= cap) continue;
    keptKeys.add(key);
    appended.push(item);
    total++;
  }

  return { merged: [...kept, ...appended], dropped, appended };
}

interface ProposedListResult<T> {
  merged: T[];
  receipt?: ListReceipt;
}

/**
 * Builds one row per matching proposed field, merges it with {@link mergeList}, and — only when the
 * list actually changed — a receipt: the dropped rows (restorable) and, per appended row, the
 * confidence and snippet a hover reads. `undefined` when nothing proposed touched this list, so a
 * fill that read nothing new leaves it untouched rather than dropping what was already there.
 */
function mergeProposedList<T extends Sourced>(
  existing: T[],
  fields: readonly ProposedField[],
  fieldKeys: readonly string[],
  buildItem: (field: ProposedField) => T,
  keyOf: (item: T) => string,
  cap: number,
): ProposedListResult<T> {
  const relevant = fields.filter((field) => fieldKeys.includes(field.fieldKey) && field.origin === "document");
  if (relevant.length === 0) return { merged: existing };

  const fieldByItem = new Map<T, ProposedField>();
  const proposed = relevant.map((field) => {
    const item = buildItem(field);
    fieldByItem.set(item, field);
    return item;
  });

  const { merged, dropped, appended } = mergeList(existing, proposed, keyOf, cap);
  if (dropped.length === 0 && appended.length === 0) return { merged };

  const appendedReceipt: ListReceipt["appended"] = {};
  for (const item of appended) {
    const field = fieldByItem.get(item);
    if (field) appendedReceipt[keyOf(item)] = { confidence: field.confidence, snippet: field.snippet };
  }
  return { merged, receipt: { dropped, appended: appendedReceipt } };
}

/**
 * The em-dash convention a benefit's name and frequency also pack values with — one constant rather
 * than the same three characters typed out twice. Lives here rather than in `lib/competencyRows.ts`
 * since the review-then-accept panel that used to unpack a proposal there is gone with #442; a fill is
 * the only reader left.
 */
const PACK_SEPARATOR = " — ";

/**
 * Unpacks a proposed competency's `"<name> — <weight> — <description>"` value. `PositionAssessmentProposer`
 * never proposes a description without also packing a weight segment ahead of it (defaulting to "0"
 * when the document gave it none), and never proposes a name containing the separator itself, so the
 * second segment, when present, is always the weight — splitting on the separator and reading
 * positionally is unambiguous.
 */
function competencyFrom(value: string): Pick<Competency, "name" | "weight" | "description"> {
  const [name = value, weightToken, ...rest] = value.split(PACK_SEPARATOR);
  const weight = Number(weightToken);
  return {
    name,
    weight: Number.isFinite(weight) ? weight : 0,
    description: rest.length > 0 ? rest.join(PACK_SEPARATOR) : null,
  };
}

/**
 * Splits each unstated competency's share of what a panel has left after every `MANUAL` row and every
 * `DOCUMENT` row the document actually gave a weight to (the proposer packs an omitted weight as
 * `"0"`). Leaves the panel exactly as it was once nothing is left unstated, or once the stated rows
 * already reach 100 — a panel a fill cannot balance is left to read red exactly as a hand-built one
 * would, rather than being forced to a total that hides the gap.
 */
export function balancePanelWeights(rows: Competency[]): Competency[] {
  const isStated = (row: Competency) => row.source === "MANUAL" || (row.source === "DOCUMENT" && row.weight > 0);
  const unstated = rows.filter((row) => !isStated(row));
  if (unstated.length === 0) return rows;

  const stated = rows.filter(isStated).reduce((sum, row) => sum + row.weight, 0);
  if (stated >= 100) return rows;

  const remainder = 100 - stated;
  const share = Math.floor(remainder / unstated.length);
  const leftover = remainder - share * unstated.length;

  let index = 0;
  return rows.map((row) => {
    if (isStated(row)) return row;
    const weight = share + (index < leftover ? 1 : 0);
    index++;
    return { ...row, weight };
  });
}

// ---------------------------------------------------------------------------------------------------
// Per-section fills — details and context, the two halves of the Role Brief screen that are one draft
// object each. Reporting and assessment are folded directly in fillBrief, since their receipts split
// or combine across screen boundaries in ways these two do not.
// ---------------------------------------------------------------------------------------------------

interface SectionFillResult<D> {
  draft: D;
  scalars: StepReceipt["scalars"];
  lists: StepReceipt["lists"];
  changed: boolean;
}

function fillDetails(
  details: PositionDetails,
  extraction: PositionExtraction | undefined,
  skipped: SkippedField[],
): SectionFillResult<PositionDetails> {
  if (!extraction) return { draft: details, scalars: {}, lists: {}, changed: false };
  const fields = extraction.fields;
  const scalars: StepReceipt["scalars"] = {};
  let draft = details;
  let changed = false;

  const applyLens = <V,>(lens: ScalarLens<PositionDetails, V>) => {
    const field = fields.find((candidate) => candidate.fieldKey === lens.key);
    const result = fillScalar(draft, field, lens);
    draft = result.draft;
    if (result.changed) {
      changed = true;
      scalars[lens.key] = result.receipt;
    }
    if (result.skipped) skipped.push({ step: "details", fieldKey: lens.key, reason: result.skipped });
  };

  // The document's title always wins, typed or not — it renames the mandate. It carries no provenance
  // (the server's field-source allow-list has no key for it), so it takes no receipt and no Undo.
  const title = fields.find((candidate) => candidate.fieldKey === "roleTitle" && candidate.origin === "document");
  const readTitle = title?.value.trim();
  if (readTitle && readTitle !== draft.roleTitle) {
    draft = { ...draft, roleTitle: readTitle };
    changed = true;
  }

  applyLens(DEPARTMENT_LENS);
  applyLens(LOCATION_CITY_LENS);
  applyLens(LOCATION_COUNTRY_LENS);
  applyLens(EMPLOYMENT_TYPE_LENS);
  applyLens(SENIORITY_LENS);
  applyLens(NARRATIVE_LENS);

  const responsibilities = mergeProposedList(
    draft.responsibilities,
    fields,
    ["responsibility"],
    (field) => ({ text: field.value, source: "DOCUMENT" as const }),
    (item) => item.text,
    RESPONSIBILITY_MAX_COUNT,
  );
  const lists: StepReceipt["lists"] = {};
  if (responsibilities.receipt) {
    lists.responsibilities = responsibilities.receipt;
    changed = true;
  }
  draft = { ...draft, responsibilities: responsibilities.merged };

  return { draft, scalars, lists, changed };
}

function fillContext(
  context: MandateContext,
  extraction: PositionExtraction | undefined,
  skipped: SkippedField[],
): SectionFillResult<MandateContext> {
  if (!extraction) return { draft: context, scalars: {}, lists: {}, changed: false };
  const fields = extraction.fields;
  const scalars: StepReceipt["scalars"] = {};
  let draft = context;
  let changed = false;

  const applyLens = <V,>(lens: ScalarLens<MandateContext, V>) => {
    const field = fields.find((candidate) => candidate.fieldKey === lens.key);
    const result = fillScalar(draft, field, lens);
    draft = result.draft;
    if (result.changed) {
      changed = true;
      scalars[lens.key] = result.receipt;
    }
    if (result.skipped) skipped.push({ step: "context", fieldKey: lens.key, reason: result.skipped });
  };

  applyLens(MANDATE_REASON_LENS);
  applyLens(BUSINESS_DRIVER_LENS);

  const priorities = mergeProposedList(
    draft.strategicPriorities,
    fields,
    ["strategicPriority"],
    (field) => ({ name: field.value, selected: true, source: "DOCUMENT" as const }),
    (item) => item.name,
    PRIORITY_MAX_COUNT,
  );
  const lists: StepReceipt["lists"] = {};
  if (priorities.receipt) {
    lists.strategicPriorities = priorities.receipt;
    changed = true;
  }
  draft = { ...draft, strategicPriorities: priorities.merged };

  return { draft, scalars, lists, changed };
}

// ---------------------------------------------------------------------------------------------------
// Undo — reverses exactly what a receipt recorded, and only while nobody has typed over it since.
// ---------------------------------------------------------------------------------------------------

/**
 * Restores one scalar to what it held before the fill, but only while it still reads `DOCUMENT`: an
 * edit made since turned it `MANUAL`, and a person's own typing always wins over an undo of a reading
 * they have already moved past.
 *
 * A screen's receipt can span more than one draft object — the Role Brief's covers details, context
 * and the reporting notice pair — so `step` here is whichever one owns `fieldKey`; the caller (#397)
 * is the one that knows which object that is.
 */
export function undoScalar<D extends { fieldSources: Record<string, FieldSource> }>(
  step: D,
  fieldKey: string,
  receipt: StepReceipt,
): D {
  const scalar = receipt.scalars[fieldKey];
  if (!scalar) return step;

  if (fieldKey === "noticePeriod") {
    if (step.fieldSources.noticeValue !== "DOCUMENT") return step;
    const previous = scalar.previousValue as { noticeValue: number | null; noticeUnit: NoticeUnit | null };
    const restoredSource = scalar.previousSource ?? "MANUAL";
    return {
      ...step,
      noticeValue: previous.noticeValue,
      noticeUnit: previous.noticeUnit,
      fieldSources: { ...step.fieldSources, noticeValue: restoredSource, noticeUnit: restoredSource },
    };
  }

  if (step.fieldSources[fieldKey] !== "DOCUMENT") return step;
  return {
    ...step,
    [fieldKey]: scalar.previousValue,
    fieldSources: { ...step.fieldSources, [fieldKey]: scalar.previousSource ?? "MANUAL" },
  } as D;
}

/**
 * Removes one `DOCUMENT` row this fill added, by the text a person recognises it by. Once none of this
 * fill's rows are left in the list, the rows it dropped come back — the same list a person would see
 * had the fill never run, once every trace of it is gone.
 */
export function undoListItem<D extends object>(
  step: D,
  listKey: string,
  text: string,
  receipt: StepReceipt,
): D {
  const record = step as Record<string, unknown>;
  const list = record[listKey];
  if (!Array.isArray(list)) return step;
  const listReceipt = receipt.lists[listKey];
  const normalised = text.trim().toLowerCase();
  const keyOf = (item: { text?: string; name?: string }) => (item.text ?? item.name ?? "").trim().toLowerCase();

  const filtered = (list as Array<Sourced & { text?: string; name?: string }>).filter(
    (item) => !((item.source ?? "MANUAL") === "DOCUMENT" && keyOf(item) === normalised),
  );
  const stillHasDocument = filtered.some((item) => (item.source ?? "MANUAL") === "DOCUMENT");
  const restored = !stillHasDocument && listReceipt ? [...filtered, ...listReceipt.dropped] : filtered;
  return { ...step, [listKey]: restored };
}

/**
 * Undoes every scalar and every list a receipt recorded, in one go — the strip's "Undo all". Only
 * meaningful against a receipt whose every scalar and list belongs to the same draft object: the
 * Assessment Criteria and Reporting screens both draft one object each, so the wiring calls this
 * directly there. The Role Brief's own receipt spans three objects, so #397 calls `undoScalar` and
 * `undoListItem` per field instead of this, once per object.
 */
export function undoStep<D extends { fieldSources: Record<string, FieldSource> }>(
  step: D,
  receipt: StepReceipt,
): D {
  let draft = step;
  for (const fieldKey of Object.keys(receipt.scalars)) {
    draft = undoScalar(draft, fieldKey, receipt);
  }
  for (const listKey of Object.keys(receipt.lists)) {
    const listReceipt = receipt.lists[listKey];
    const list = (draft as Record<string, unknown>)[listKey];
    if (!listReceipt || !Array.isArray(list)) continue;
    const filtered = (list as Sourced[]).filter((item) => (item.source ?? "MANUAL") !== "DOCUMENT");
    draft = { ...draft, [listKey]: [...filtered, ...listReceipt.dropped] };
  }
  return draft;
}
