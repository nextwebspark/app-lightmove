import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { FilterRail } from "../../../../components/ui/FilterRail";
import { FilterCheckRow } from "../../../../components/ui/FilterCheckRow";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import { TagListInput } from "../../../../components/ui/TagListInput";
import { cn } from "../../../../lib/cn";
import type {
  CompanyMatch,
  ContactDataType,
  FacetOption,
  PeopleCount,
  PeopleFacets,
  PeopleFilter,
  TitleMatch,
} from "../../api/types";
import { CompanySearchCombobox } from "../CompanySearchCombobox";
import { FacetsUnavailable } from "../FacetsUnavailable";
import { FilterAccordion, type SelectedTag } from "../../../../components/ui/FilterAccordion";
import { LocationFilter } from "./LocationFilter";

type AccordionKey =
  | "title"
  | "seniority"
  | "function"
  | "location"
  | "company"
  | "industry"
  | "experience"
  | "skills"
  | "background"
  | "contact";

/** The vocabulary-backed lists, toggled one value at a time. */
type ListAxis =
  | "seniorities"
  | "jobFunctions"
  | "companySizes"
  | "yearsInCurrentRole"
  | "yearsOfExperience"
  | "contactTypes";

const TITLE_MATCHES = [
  { value: "current", label: "Current" },
  { value: "past", label: "Past" },
  { value: "both", label: "Either" },
] as const satisfies readonly { value: TitleMatch; label: string }[];

const COMPANY_MATCHES = [
  { value: "current", label: "Current" },
  { value: "both", label: "Either" },
  { value: "past_only", label: "Past only" },
] as const satisfies readonly { value: CompanyMatch; label: string }[];

const CONTACT_TYPES: readonly FacetOption[] = [
  { value: "work_email", label: "Has a work email" },
  { value: "personal_email", label: "Has a personal email" },
  { value: "phone", label: "Has a phone" },
];

const EXCLUDED_TAG = "not:";

/** How a title or skill box explains its Boolean: ContactOut reads upper-case operators only. */
const BOOLEAN_HINT = "AND, OR and NOT in capitals combine terms: (CFO OR \"Finance Director\") NOT Assistant";

/**
 * The People rail: ContactOut's People Search filter, in its own dashboard's order, over the same
 * accordion the company rail uses. Only parameters ContactOut's API reference lists are offered —
 * its dashboard's Revenue, Gender and saved exclude lists have no API behind them.
 *
 * <p>The count at the top is free and live; the Search button at the foot is not, which is why the
 * results never follow a chip click the way the company grid does.
 */
export function PeopleFilterSidebar({
  facets,
  facetsError,
  filter,
  count,
  countPending,
  onChange,
  onSearch,
  searching,
  onClose,
}: {
  facets: PeopleFacets | undefined;
  facetsError: boolean;
  filter: PeopleFilter;
  count: PeopleCount | undefined;
  countPending: boolean;
  onChange: (filter: PeopleFilter) => void;
  onSearch: () => void;
  searching: boolean;
  onClose: () => void;
}) {
  const [open, setOpen] = useState<AccordionKey | null>("title");
  const toggleOpen = (key: AccordionKey) => setOpen((current) => (current === key ? null : key));

  const toggleValue = (axis: ListAxis, value: string) => {
    const current: string[] = filter[axis];
    const next = current.includes(value) ? current.filter((entry) => entry !== value) : [...current, value];
    onChange({ ...filter, [axis]: next });
  };

  const tagsOf = (values: string[], options?: readonly FacetOption[]): SelectedTag[] =>
    values.map((value) => ({ value, label: options?.find((option) => option.value === value)?.label ?? value }));

  const removeFrom = (key: keyof PeopleFilter, value: string) => {
    const current = filter[key];
    if (Array.isArray(current)) {
      onChange({ ...filter, [key]: (current as string[]).filter((entry) => entry !== value) });
    }
  };

  const industryTags: SelectedTag[] = [
    ...tagsOf(filter.industries),
    ...filter.excludedIndustries.map((value) => ({ value: `${EXCLUDED_TAG}${value}`, label: `Not ${value}` })),
  ];

  const industryOptions = (facets?.industries ?? []).map((industry) => ({ value: industry, label: industry }));
  const isEmpty = isEmptyFilter(filter);

  return (
    <FilterRail label="People filters" onClose={onClose}>
      <MatchCount count={count} pending={countPending} isEmpty={isEmpty} />

      <div className="min-h-0 flex-1 overflow-y-auto">
        <FilterAccordion
          label="Job title"
          selected={[
            ...(filter.name ? [{ value: "name", label: filter.name }] : []),
            ...tagsOf(filter.jobTitles),
            ...filter.excludedJobTitles.map((value) => ({ value: `${EXCLUDED_TAG}${value}`, label: `Not ${value}` })),
          ]}
          onRemove={(value) => {
            if (value === "name") onChange({ ...filter, name: null });
            else if (value.startsWith(EXCLUDED_TAG)) removeFrom("excludedJobTitles", value.slice(EXCLUDED_TAG.length));
            else removeFrom("jobTitles", value);
          }}
          open={open === "title"}
          onToggleOpen={() => toggleOpen("title")}
          onReset={() =>
            onChange({
              ...filter,
              name: null,
              jobTitles: [],
              excludedJobTitles: [],
              titleMatch: null,
              includeRelatedTitles: false,
              recentlyChangedJobs: false,
            })
          }
        >
          <Stack>
            <TagListInput
              values={filter.jobTitles}
              onChange={(jobTitles) => onChange({ ...filter, jobTitles })}
              placeholder="CFO, Head of Finance…"
              ariaLabel="Job titles"
              maxItems={50}
            />
            <Hint>{BOOLEAN_HINT}</Hint>
            <SegmentedControl
              label="Match titles held"
              options={TITLE_MATCHES}
              value={filter.titleMatch ?? "current"}
              onChange={(titleMatch) => onChange({ ...filter, titleMatch: titleMatch === "current" ? null : titleMatch })}
            />
            <FilterCheckRow
              label="Include related titles"
              checked={filter.includeRelatedTitles}
              onToggle={() => onChange({ ...filter, includeRelatedTitles: !filter.includeRelatedTitles })}
            />
            <FilterCheckRow
              label="Changed jobs in the last 3 months"
              checked={filter.recentlyChangedJobs}
              onToggle={() =>
                // ContactOut refuses this beside years in current role, so choosing one clears the other.
                onChange({
                  ...filter,
                  recentlyChangedJobs: !filter.recentlyChangedJobs,
                  yearsInCurrentRole: filter.recentlyChangedJobs ? filter.yearsInCurrentRole : [],
                })
              }
            />
            <Label>Exclude titles</Label>
            <TagListInput
              values={filter.excludedJobTitles}
              onChange={(excludedJobTitles) => onChange({ ...filter, excludedJobTitles })}
              placeholder="Assistant, Intern…"
              ariaLabel="Excluded job titles"
              maxItems={50}
            />
            <Label>Name</Label>
            <input
              value={filter.name ?? ""}
              onChange={(event) => onChange({ ...filter, name: event.target.value || null })}
              placeholder="e.g. Sara Al Mansoori"
              aria-label="Name"
              maxLength={120}
              className="h-8 w-full rounded-md border border-u-border-strong bg-u-raised px-[10px] text-note text-u-text outline-none placeholder:text-u-text3"
            />
          </Stack>
        </FilterAccordion>

        <VocabularyAccordion
          label="Seniority"
          options={facets?.seniorities}
          unavailable={facetsError}
          selected={filter.seniorities}
          open={open === "seniority"}
          onToggleOpen={() => toggleOpen("seniority")}
          onToggle={(value) => toggleValue("seniorities", value)}
          onReset={() => onChange({ ...filter, seniorities: [] })}
        />

        <VocabularyAccordion
          label="Job function"
          options={facets?.jobFunctions}
          unavailable={facetsError}
          selected={filter.jobFunctions}
          open={open === "function"}
          onToggleOpen={() => toggleOpen("function")}
          onToggle={(value) => toggleValue("jobFunctions", value)}
          onReset={() => onChange({ ...filter, jobFunctions: [] })}
        />

        <FilterAccordion
          label="Location"
          selected={tagsOf(filter.locations)}
          onRemove={(value) => removeFrom("locations", value)}
          open={open === "location"}
          onToggleOpen={() => toggleOpen("location")}
          onReset={() => onChange({ ...filter, locations: [], locationRadius: null })}
        >
          <LocationFilter
            locations={filter.locations}
            radius={filter.locationRadius}
            onChange={(locations, locationRadius) => onChange({ ...filter, locations, locationRadius })}
          />
        </FilterAccordion>

        <FilterAccordion
          label="Company"
          selected={[
            ...tagsOf(filter.companies),
            ...tagsOf(filter.domains),
            ...tagsOf(filter.companySizes, facets?.companySizes),
            ...filter.excludedCompanies.map((value) => ({ value: `${EXCLUDED_TAG}${value}`, label: `Not ${value}` })),
          ]}
          onRemove={(value) => {
            if (value.startsWith(EXCLUDED_TAG)) removeFrom("excludedCompanies", value.slice(EXCLUDED_TAG.length));
            else if (filter.domains.includes(value)) removeFrom("domains", value);
            else if (filter.companySizes.includes(value)) removeFrom("companySizes", value);
            else removeFrom("companies", value);
          }}
          open={open === "company"}
          onToggleOpen={() => toggleOpen("company")}
          onReset={() =>
            onChange({
              ...filter,
              companies: [],
              domains: [],
              companyMatch: null,
              excludedCompanies: [],
              companySizes: [],
            })
          }
        >
          <Stack>
            <Label>From the market</Label>
            <CompanySearchCombobox
              listId="people-company-picker"
              excludedIds={EMPTY_IDS}
              onPick={(company) => {
                const domain = domainOf(company.website);
                if (domain) {
                  if (!filter.domains.includes(domain)) onChange({ ...filter, domains: [...filter.domains, domain] });
                } else if (!filter.companies.includes(company.companyName)) {
                  onChange({ ...filter, companies: [...filter.companies, company.companyName] });
                }
              }}
            />
            <Hint>A company picked here is matched by its website, which is surer than its name.</Hint>
            <Label>By name</Label>
            <TagListInput
              values={filter.companies}
              onChange={(companies) => onChange({ ...filter, companies })}
              placeholder="Any company, by name…"
              ariaLabel="Companies"
              maxItems={50}
            />
            {filter.domains.length > 0 && (
              <TagListInput
                values={filter.domains}
                onChange={(domains) => onChange({ ...filter, domains })}
                ariaLabel="Company websites"
                maxItems={50}
              />
            )}
            <SegmentedControl
              label="Match companies"
              options={COMPANY_MATCHES}
              value={filter.companyMatch ?? "current"}
              onChange={(companyMatch) =>
                onChange({ ...filter, companyMatch: companyMatch === "current" ? null : companyMatch })
              }
            />
            <Label>Exclude companies</Label>
            <TagListInput
              values={filter.excludedCompanies}
              onChange={(excludedCompanies) => onChange({ ...filter, excludedCompanies })}
              placeholder="Current employers to leave out…"
              ariaLabel="Excluded companies"
              maxItems={50}
            />
            <Hint>This mandate&rsquo;s declined companies are always left out.</Hint>
            <Label>Company size</Label>
            <CheckRows
              options={facets?.companySizes}
              unavailable={facetsError}
              selected={filter.companySizes}
              onToggle={(value) => toggleValue("companySizes", value)}
            />
          </Stack>
        </FilterAccordion>

        <FilterAccordion
          label="Industry"
          selected={industryTags}
          onRemove={(value) => {
            if (value.startsWith(EXCLUDED_TAG)) removeFrom("excludedIndustries", value.slice(EXCLUDED_TAG.length));
            else removeFrom("industries", value);
          }}
          open={open === "industry"}
          onToggleOpen={() => toggleOpen("industry")}
          onReset={() => onChange({ ...filter, industries: [], excludedIndustries: [] })}
        >
          {facetsError ? (
            <FacetsUnavailable />
          ) : (
            <Stack>
              <VocabularyTags
                values={filter.industries}
                options={industryOptions}
                onChange={(industries) => onChange({ ...filter, industries })}
                ariaLabel="Industries"
                listId="people-industries"
                placeholder="Software Development…"
              />
              <Label>Exclude industries</Label>
              <VocabularyTags
                values={filter.excludedIndustries}
                options={industryOptions}
                onChange={(excludedIndustries) => onChange({ ...filter, excludedIndustries })}
                ariaLabel="Excluded industries"
                listId="people-excluded-industries"
                placeholder="Staffing and Recruiting…"
              />
            </Stack>
          )}
        </FilterAccordion>

        <FilterAccordion
          label="Experience"
          selected={[
            ...tagsOf(filter.yearsInCurrentRole, facets?.yearsInCurrentRole).map((tag) => ({
              ...tag,
              label: `In role ${tag.label.toLowerCase()}`,
            })),
            ...tagsOf(filter.yearsOfExperience, facets?.yearsOfExperience).map((tag) => ({
              value: `total:${tag.value}`,
              label: `Career ${tag.label.toLowerCase()}`,
            })),
          ]}
          onRemove={(value) =>
            value.startsWith("total:")
              ? removeFrom("yearsOfExperience", value.slice("total:".length))
              : removeFrom("yearsInCurrentRole", value)
          }
          open={open === "experience"}
          onToggleOpen={() => toggleOpen("experience")}
          onReset={() => onChange({ ...filter, yearsInCurrentRole: [], yearsOfExperience: [] })}
        >
          <Stack>
            <Label>Years in current role</Label>
            {filter.recentlyChangedJobs ? (
              <Hint>Not with &ldquo;Changed jobs in the last 3 months&rdquo; — ContactOut takes one or the other.</Hint>
            ) : (
              <CheckRows
                options={facets?.yearsInCurrentRole}
                unavailable={facetsError}
                selected={filter.yearsInCurrentRole}
                onToggle={(value) => toggleValue("yearsInCurrentRole", value)}
              />
            )}
            <Label>Total years of experience</Label>
            <CheckRows
              options={facets?.yearsOfExperience}
              unavailable={facetsError}
              selected={filter.yearsOfExperience}
              onToggle={(value) => toggleValue("yearsOfExperience", value)}
            />
          </Stack>
        </FilterAccordion>

        <FilterAccordion
          label="Skills & keyword"
          selected={[...tagsOf(filter.skills), ...(filter.keyword ? [{ value: "keyword", label: filter.keyword }] : [])]}
          onRemove={(value) => (value === "keyword" ? onChange({ ...filter, keyword: null }) : removeFrom("skills", value))}
          open={open === "skills"}
          onToggleOpen={() => toggleOpen("skills")}
          onReset={() => onChange({ ...filter, skills: [], keyword: null })}
        >
          <Stack>
            <TagListInput
              values={filter.skills}
              onChange={(skills) => onChange({ ...filter, skills })}
              placeholder="IFRS, Treasury…"
              ariaLabel="Skills"
              maxItems={50}
            />
            <Label>Keyword anywhere on the profile</Label>
            <input
              value={filter.keyword ?? ""}
              onChange={(event) => onChange({ ...filter, keyword: event.target.value || null })}
              placeholder="e.g. IPO"
              aria-label="Keyword"
              maxLength={500}
              className="h-8 w-full rounded-md border border-u-border-strong bg-u-raised px-[10px] text-note text-u-text outline-none placeholder:text-u-text3"
            />
            <Hint>{BOOLEAN_HINT}</Hint>
          </Stack>
        </FilterAccordion>

        <FilterAccordion
          label="Languages & education"
          selected={[
            ...filter.languages.map((language) => ({ value: `lang:${language.language}`, label: language.language })),
            ...tagsOf(filter.education),
          ]}
          onRemove={(value) =>
            value.startsWith("lang:")
              ? onChange({
                  ...filter,
                  languages: filter.languages.filter((language) => `lang:${language.language}` !== value),
                })
              : removeFrom("education", value)
          }
          open={open === "background"}
          onToggleOpen={() => toggleOpen("background")}
          onReset={() => onChange({ ...filter, languages: [], education: [] })}
        >
          <Stack>
            <Label>Languages spoken</Label>
            <TagListInput
              values={filter.languages.map((language) => language.language)}
              onChange={(names) =>
                onChange({
                  ...filter,
                  languages: names.map(
                    (name) =>
                      filter.languages.find((language) => language.language === name) ?? {
                        language: name,
                        proficiencies: [],
                      },
                  ),
                })
              }
              placeholder="Arabic, French…"
              ariaLabel="Languages"
              maxItems={20}
            />
            <Label>School or degree</Label>
            <TagListInput
              values={filter.education}
              onChange={(education) => onChange({ ...filter, education })}
              placeholder="INSEAD, MBA…"
              ariaLabel="Schools or degrees"
              maxItems={50}
            />
          </Stack>
        </FilterAccordion>

        <FilterAccordion
          label="Contact on file"
          selected={tagsOf(filter.contactTypes, CONTACT_TYPES)}
          onRemove={(value) => removeFrom("contactTypes", value)}
          open={open === "contact"}
          onToggleOpen={() => toggleOpen("contact")}
          onReset={() => onChange({ ...filter, contactTypes: [] })}
        >
          <Stack>
            <CheckRows
              options={CONTACT_TYPES}
              unavailable={false}
              selected={filter.contactTypes}
              onToggle={(value) => toggleValue("contactTypes", value as ContactDataType)}
            />
            <Hint>Only people ContactOut holds one for. Nothing is revealed or bought by it.</Hint>
          </Stack>
        </FilterAccordion>
      </div>

      <div className="border-t border-u-border bg-u-surface p-3">
        <button
          type="button"
          onClick={onSearch}
          disabled={isEmpty || searching || count?.offered === false}
          className="flex w-full items-center justify-center gap-2 rounded-[7px] bg-u-accent-solid px-4 py-2.5 text-note font-semibold text-white transition hover:brightness-105 disabled:opacity-40"
        >
          <Icon d={ICONS.search} size={14} />
          {searching ? "Searching…" : "Search"}
        </button>
        <p className="mt-1.5 text-center text-meta text-u-text3">
          The top 25 · up to 25 search credits, none for a page already fetched
        </p>
      </div>
    </FilterRail>
  );
}

/** The live, free count over the stored filter — the one number that moves as chips are clicked. */
function MatchCount({ count, pending, isEmpty }: { count: PeopleCount | undefined; pending: boolean; isEmpty: boolean }) {
  if (count?.offered === false) {
    return (
      <div className="border-b border-u-border px-4 py-3 text-note text-u-text3">
        People search isn't switched on for your workspace. Contact Uncava support to turn it on.
      </div>
    );
  }
  return (
    <div className="border-b border-u-border px-4 py-3" aria-live="polite">
      {isEmpty ? (
        <p className="text-note text-u-text3">Add a filter to count who matches.</p>
      ) : (
        <>
          <div className="flex items-center justify-between gap-2">
            <span className="type-summary-label text-u-text3">People match</span>
            {pending && <span className="text-meta text-u-text3">Updating…</span>}
          </div>
          <p
            className={cn(
              "mt-0.5 text-title font-semibold tabular-nums tracking-tight text-u-text transition-opacity",
              pending && "opacity-50",
            )}
          >
            {count ? count.total.toLocaleString() : "—"}
          </p>
          {count && count.total > 0 && (
            <dl
              className="mt-2.5 grid grid-cols-3 gap-1.5"
              title="ContactOut's estimate of how many of them it holds each contact for"
            >
              <ContactStat icon={ICONS.mail} label="Work email" value={count.estimatedWorkEmails} />
              <ContactStat icon={ICONS.mail} label="Personal" value={count.estimatedPersonalEmails} />
              <ContactStat icon={ICONS.phone} label="Phone" value={count.estimatedPhones} />
            </dl>
          )}
        </>
      )}
    </div>
  );
}

const COMPACT = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

/** One estimated contact figure: compact, because three must share a rail a third of the width each. */
function ContactStat({ icon, label, value }: { icon: string; label: string; value: number }) {
  return (
    <div className="min-w-0 rounded-[6px] border border-u-border bg-u-raised px-2 py-1.5">
      <dt className="flex items-center gap-1 truncate text-eyebrow text-u-text3">
        <Icon d={icon} size={11} className="flex-none" />
        {label}
      </dt>
      <dd className="mt-0.5 text-note font-semibold tabular-nums text-u-text2" title={`~${value.toLocaleString()}`}>
        ~{COMPACT.format(value)}
      </dd>
    </div>
  );
}

function VocabularyAccordion({
  label,
  options,
  unavailable,
  selected,
  open,
  onToggleOpen,
  onToggle,
  onReset,
}: {
  label: string;
  options: readonly FacetOption[] | undefined;
  unavailable: boolean;
  selected: string[];
  open: boolean;
  onToggleOpen: () => void;
  onToggle: (value: string) => void;
  onReset: () => void;
}) {
  return (
    <FilterAccordion
      label={label}
      selected={selected.map((value) => ({
        value,
        label: options?.find((option) => option.value === value)?.label ?? value,
      }))}
      onRemove={onToggle}
      open={open}
      onToggleOpen={onToggleOpen}
      onReset={onReset}
    >
      <CheckRows options={options} unavailable={unavailable} selected={selected} onToggle={onToggle} />
    </FilterAccordion>
  );
}

function CheckRows({
  options,
  unavailable,
  selected,
  onToggle,
}: {
  options: readonly FacetOption[] | undefined;
  unavailable: boolean;
  selected: string[];
  onToggle: (value: string) => void;
}) {
  if (unavailable) return <FacetsUnavailable />;
  if (!options) return <div className="h-24 animate-pulse rounded-md bg-u-raised" />;
  return (
    <div className="flex flex-col gap-[2px]">
      {options.map((option) => (
        <FilterCheckRow
          key={option.value}
          label={option.label}
          checked={selected.includes(option.value)}
          onToggle={() => onToggle(option.value)}
        />
      ))}
    </div>
  );
}

/** A tag box that only takes the vocabulary's own values: a typed industry ContactOut does not list matches nobody. */
function VocabularyTags({
  values,
  options,
  onChange,
  ariaLabel,
  listId,
  placeholder,
}: {
  values: string[];
  options: { value: string; label: string }[];
  onChange: (values: string[]) => void;
  ariaLabel: string;
  listId: string;
  placeholder: string;
}) {
  const known = new Set(options.map((option) => option.value));
  return (
    <TagListInput
      values={values}
      onChange={(next) => onChange(next.filter((value) => known.has(value)))}
      options={options}
      listId={listId}
      placeholder={placeholder}
      ariaLabel={ariaLabel}
      maxItems={50}
    />
  );
}

function Stack({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-2">{children}</div>;
}

function Label({ children }: { children: ReactNode }) {
  return <span className="mt-1 text-eyebrow font-semibold uppercase tracking-[0.08em] text-u-text3">{children}</span>;
}

function Hint({ children }: { children: ReactNode }) {
  return <p className="text-meta leading-relaxed text-u-text3">{children}</p>;
}

const EMPTY_IDS = new Set<string>();

/** "https://www.dpworld.com/about" → "dpworld.com"; ContactOut keys a company on its domain. */
export function domainOf(website: string | null): string | null {
  if (!website) return null;
  try {
    const url = new URL(website.includes("://") ? website : `https://${website}`);
    return url.hostname.replace(/^www\./, "") || null;
  } catch {
    return null;
  }
}

/** Mirrors the server's `PeopleFilter.isEmpty`: with nothing to search on, ContactOut would answer its whole index. */
export function isEmptyFilter(filter: PeopleFilter): boolean {
  return (
    !filter.name &&
    !filter.keyword &&
    !filter.recentlyChangedJobs &&
    [
      filter.jobTitles,
      filter.seniorities,
      filter.jobFunctions,
      filter.skills,
      filter.yearsInCurrentRole,
      filter.yearsOfExperience,
      filter.locations,
      filter.companies,
      filter.domains,
      filter.companySizes,
      filter.industries,
      filter.languages,
      filter.education,
    ].every((list) => list.length === 0)
  );
}
