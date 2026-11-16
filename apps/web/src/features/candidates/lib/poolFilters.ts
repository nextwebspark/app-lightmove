import type { Dispatch, SetStateAction } from "react";
import type { DataGridColumnFilter } from "../../../lib/gridFilters";
import { CANDIDATE_SENIORITIES, CANDIDATE_SOURCE_STYLES } from "./candidateVocabulary";
import { liveStages, liveStatuses, offeredStatusLabel } from "./pipelineStatus";
import type { Project } from "../../projects/api/types";
import type { Member, PipelineCatalog } from "../../workspace/api/types";
import type {
  CandidateSource,
  CandidateTag,
  PoolContactFilter,
  PoolExperienceBand,
  PoolFilters,
  PoolPresence,
  PoolRecencyBand,
} from "../api/types";

/** The Candidates page with nothing narrowed: everyone, newest activity first. */
export const NO_POOL_FILTERS: PoolFilters = {
  q: "",
  view: "all",
  name: "",
  title: "",
  company: "",
  tagIds: [],
  positions: [],
  statuses: [],
  pipelineStages: [],
  owners: [],
  countries: [],
  linkedin: [],
  contacts: [],
  experience: [],
  activity: [],
  seniorities: [],
  sources: [],
  added: [],
  doNotContact: [],
  sort: "activity",
  direction: "desc",
};

/** The column filters typed rather than ticked, held apart so a keystroke waits for the debounce. */
export type PoolTextFilters = Pick<PoolFilters, "name" | "title" | "company">;

export const NOBODY = "nobody";
export const NO_SENIORITY = "none";
const STAGE_PREFIX = "stage:";

type Option<T extends string = string> = { value: T; label: string; indent?: boolean };

const PRESENCE: Option<PoolPresence>[] = [
  { value: "yes", label: "Yes" },
  { value: "no", label: "No" },
];

const LINKEDIN: Option<PoolPresence>[] = [
  { value: "yes", label: "Has a LinkedIn profile" },
  { value: "no", label: "No LinkedIn profile" },
];

const CONTACTS: Option<PoolContactFilter>[] = [
  { value: "email", label: "Email on file" },
  { value: "phone", label: "Phone on file" },
  { value: "none", label: "Neither on file" },
];

const EXPERIENCE: Option<PoolExperienceBand>[] = [
  { value: "lt5", label: "Under 5 yrs" },
  { value: "5to10", label: "5–10 yrs" },
  { value: "10to15", label: "10–15 yrs" },
  { value: "15to20", label: "15–20 yrs" },
  { value: "20plus", label: "20+ yrs" },
  { value: "unknown", label: "Not recorded" },
];

const ADDED: Option<PoolRecencyBand>[] = [
  { value: "week", label: "Past week" },
  { value: "month", label: "Past month" },
  { value: "quarter", label: "Past 3 months" },
  { value: "older", label: "Over 3 months ago" },
];

const ACTIVITY: Option<PoolRecencyBand>[] = [...ADDED, { value: "never", label: "Nothing recorded" }];

export interface PoolFilterSources {
  tags: CandidateTag[];
  staff: Member[];
  positions: Project[];
  countries: string[];
  catalog: PipelineCatalog;
}

/**
 * The Candidates grid's column filters, keyed by column id — each column's header menu edits its own, and the
 * toolbar's Filter button lists them. The typed ones edit `text`, which reaches `filters` after the debounce.
 *
 * <p>Every change is an updater, never a spread of the `filters` this render holds: Clear all calls each
 * filter's `onChange` in one tick, and spreading a stale object would put back every filter but the last.
 */
export function poolColumnFilters(
  filters: PoolFilters,
  onChange: Dispatch<SetStateAction<PoolFilters>>,
  text: PoolTextFilters,
  onTextChange: Dispatch<SetStateAction<PoolTextFilters>>,
  sources: PoolFilterSources,
): Record<string, DataGridColumnFilter> {
  const typed = (key: keyof PoolTextFilters, label: string): DataGridColumnFilter => ({
    label,
    value: text[key],
    onChange: (value) => onTextChange((current) => ({ ...current, [key]: value })),
    "aria-label": `Filter by ${label.toLowerCase()}`,
  });
  function ticked<K extends keyof PoolFilters>(
    key: K,
    label: string,
    options: Option[],
  ): DataGridColumnFilter {
    return {
      kind: "check",
      label,
      options,
      selected: filters[key] as string[],
      onChange: (selected) => onChange((current) => ({ ...current, [key]: selected })),
      "aria-label": `Filter by ${label.toLowerCase()}`,
    };
  }

  return {
    name: typed("name", "Person"),
    links: ticked("linkedin", "LinkedIn", LINKEDIN),
    title: typed("title", "Title"),
    company: typed("company", "Company"),
    location: ticked(
      "countries",
      "Country",
      withSelected(
        sources.countries.map((country) => ({ value: country, label: country })),
        filters.countries,
      ),
    ),
    experience: ticked("experience", "Experience", EXPERIENCE),
    seniority: ticked("seniorities", "Seniority", [
      ...CANDIDATE_SENIORITIES.map((tier) => ({ value: tier, label: tier })),
      { value: NO_SENIORITY, label: "Not recorded" },
    ]),
    contact: ticked("contacts", "Contact", CONTACTS),
    positions: ticked(
      "positions",
      "Positions",
      sources.positions.map((position) => ({ value: position.id, label: position.positionTitle })),
    ),
    status: statusFilter(filters, onChange, sources.catalog),
    tags: ticked(
      "tagIds",
      "Tags",
      sources.tags.map((tag) => ({ value: tag.id, label: tag.retired ? `${tag.label} (retired)` : tag.label })),
    ),
    owner: ticked("owners", "Owner", [
      { value: NOBODY, label: "Nobody" },
      ...sources.staff.map((member) => ({ value: member.userId, label: member.fullName })),
    ]),
    source: ticked(
      "sources",
      "Source",
      (Object.keys(CANDIDATE_SOURCE_STYLES) as CandidateSource[]).map((source) => ({
        value: source,
        label: CANDIDATE_SOURCE_STYLES[source].label,
      })),
    ),
    added: ticked("added", "Added", ADDED),
    doNotContact: ticked("doNotContact", "Do not contact", PRESENCE),
    activity: ticked("activity", "Last activity", ACTIVITY),
  };
}

/**
 * Stage and status as one list, as In universe's Status column offers them: "All {stage}" and each of its
 * statuses beneath. A ticked stage is every status under it; the server reads either from any position.
 */
function statusFilter(
  filters: PoolFilters,
  onChange: Dispatch<SetStateAction<PoolFilters>>,
  catalog: PipelineCatalog,
): DataGridColumnFilter {
  const options: Option[] = [];
  for (const stage of liveStages(catalog)) {
    options.push({ value: STAGE_PREFIX + stage.key, label: `All ${stage.label}` });
    for (const status of liveStatuses(stage)) {
      options.push({ value: status.key, label: offeredStatusLabel(stage, status), indent: true });
    }
  }
  return {
    kind: "check",
    label: "Status",
    options,
    selected: [...filters.pipelineStages.map((key) => STAGE_PREFIX + key), ...filters.statuses],
    onChange: (selected) =>
      onChange((current) => ({
        ...current,
        pipelineStages: selected.filter((value) => value.startsWith(STAGE_PREFIX)).map((value) => value.slice(STAGE_PREFIX.length)),
        statuses: selected.filter((value) => !value.startsWith(STAGE_PREFIX)),
      })),
    "aria-label": "Filter by status",
  };
}

/** A ticked value the list no longer offers stays listed, so it can still be unticked. */
function withSelected(options: Option[], selected: readonly string[]): Option[] {
  const missing = selected.filter((value) => !options.some((option) => option.value === value));
  return [...options, ...missing.map((value) => ({ value, label: value }))];
}
