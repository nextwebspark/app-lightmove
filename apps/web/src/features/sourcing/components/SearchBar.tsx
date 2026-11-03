import { useState, type KeyboardEvent, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { FilterCheckRow } from "../../../components/ui/FilterCheckRow";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { Country } from "../../../lib/countries";
import type { CompanyCount, SavedCriteria, SearchCriteria, SearchLevel, SourcingCounts } from "../api/sourcingApi";
import { LEVEL_OPTIONS, levelsSummary } from "../lib/searchLevels";

const FIELD =
  "inline-flex h-8 items-center gap-1.5 whitespace-nowrap rounded-md border border-u-border-strong bg-u-surface px-2.5 text-note font-medium hover:bg-u-raised";
const POPOVER_HEADING = "px-2 pb-1 pt-1.5 text-meta font-semibold uppercase tracking-[.06em] text-u-text3";
const MAX_EXCLUDED = 4;
const MAX_TERMS = 12;

/** A C-suite abbreviation ("CIO") is a whole title on its own; every other word is a title word. */
const STANDALONE = /^C[A-Z]{1,3}O$/;

/**
 * The search, drafted from the brief and changed in place: levels and title words with free counts, the words left
 * out, where people may live, which companies, and how many profiles a company may buy. Search is the only press
 * that spends.
 */
export function SearchBar({
  criteria,
  onChange,
  source,
  briefChanged,
  drafting,
  onReset,
  counts,
  counting,
  companies,
  isIncluded,
  onToggleCompany,
  onAllCompanies,
  perCompanyChoices,
  countries,
  countryName,
  searchLabel,
  searchDisabled,
  onSearch,
}: {
  criteria: SearchCriteria | null;
  onChange: (criteria: SearchCriteria) => void;
  source: SavedCriteria["source"] | null;
  briefChanged: boolean;
  drafting: boolean;
  onReset: () => void;
  counts: SourcingCounts | null;
  counting: boolean;
  companies: CompanyCount[];
  isIncluded: (companyId: string) => boolean;
  onToggleCompany: (companyId: string) => void;
  onAllCompanies: (included: boolean) => void;
  perCompanyChoices: number[];
  countries: Country[];
  countryName: (code: string) => string;
  searchLabel: string;
  searchDisabled: boolean;
  onSearch: () => void;
}) {
  const included = companies.filter((company) => isIncluded(company.triageCompanyId));
  const countLine = !criteria
    ? "Reading the brief…"
    : counting && !counts
      ? "Counting…"
      : counts?.counted
        ? `≈ ${sum(included, "match")} match · ${sum(included, "toBuy")} to buy · ${sum(included, "onFile")} already on file`
        : "";
  return (
    <div className="relative flex-none border-b border-u-border bg-u-sunken px-[18px] pb-3.5 pt-3">
      <div className="mb-2 flex items-center gap-2 text-note font-medium text-u-text3">
        <Icon d={ICONS.sparkle} size={14} className={cn(source === "AI" && "text-u-inferred")} />
        <span>{noteOf(source, briefChanged, drafting)}</span>
        {!drafting && source !== null && (
          <>
            <span>·</span>
            <button type="button" onClick={onReset} className="font-medium text-u-accent hover:text-u-accent-hover">
              Reset
            </button>
          </>
        )}
      </div>
      {criteria ? (
        <>
          <div className="flex flex-wrap items-stretch gap-2">
            <LevelField criteria={criteria} onChange={onChange} counts={counts} companyCount={included.length} />
            <TitlesField criteria={criteria} onChange={onChange} counts={counts} />
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <LeaveOutField criteria={criteria} onChange={onChange} />
            <WhereField criteria={criteria} onChange={onChange} countries={countries} countryName={countryName} />
            <CompaniesField
              companies={companies}
              included={included.length}
              isIncluded={isIncluded}
              onToggle={onToggleCompany}
              onAll={onAllCompanies}
            />
            <label className={FIELD}>
              <span className="sr-only">Profiles bought per company</span>
              <select
                value={criteria.perCompany}
                onChange={(event) => onChange({ ...criteria, perCompany: Number(event.target.value) })}
                className="bg-transparent font-semibold text-u-text outline-none"
              >
                {perCompanyChoices.map((choice) => (
                  <option key={choice} value={choice}>
                    {choice}
                  </option>
                ))}
              </select>
              <span className="text-u-text3">bought per company</span>
            </label>
            <span className="flex-1" />
            <span className="font-mono text-note text-u-text2">{countLine}</span>
            <button
              type="button"
              onClick={onSearch}
              disabled={searchDisabled}
              className="inline-flex h-[34px] items-center gap-1.5 rounded-md bg-u-accent-solid px-3.5 text-body font-semibold text-white hover:bg-u-accent-solid-hover disabled:opacity-50"
            >
              <Icon d={ICONS.search} size={14} />
              {searchLabel}
            </button>
          </div>
        </>
      ) : (
        <div className="flex gap-2" aria-hidden="true">
          {[96, 280, 120].map((width) => (
            <span key={width} className="h-8 animate-pulse rounded-md bg-u-border" style={{ width }} />
          ))}
        </div>
      )}
    </div>
  );
}

function noteOf(source: SavedCriteria["source"] | null, briefChanged: boolean, drafting: boolean): string {
  if (drafting) return "Drafting the search from the brief…";
  if (briefChanged) return "The brief has changed since this search was drafted";
  if (source === "EDITED") return "Your search";
  if (source === "DEFAULT") return "From the role title";
  return "Suggested from the brief";
}

function sum(companies: CompanyCount[], field: "match" | "toBuy" | "onFile"): number {
  return companies.reduce((total, company) => total + company[field], 0);
}

function LevelField({
  criteria,
  onChange,
  counts,
  companyCount,
}: {
  criteria: SearchCriteria;
  onChange: (criteria: SearchCriteria) => void;
  counts: SourcingCounts | null;
  companyCount: number;
}) {
  const countOf = (level: SearchLevel) => counts?.levels.find((count) => count.level === level)?.count;
  const toggle = (level: SearchLevel) =>
    onChange({
      ...criteria,
      levels: criteria.levels.includes(level)
        ? criteria.levels.filter((picked) => picked !== level)
        : [...criteria.levels, level],
    });
  return (
    <Popover
      width={360}
      hasPopup="listbox"
      label="Levels to search"
      triggerClassName={FIELD}
      trigger={() => (
        <>
          <span className="text-u-text3">Level</span>
          <span className="max-w-[260px] truncate text-u-text">{levelsSummary(criteria.levels)}</span>
          <Icon d={ICONS.chevronDown} size={12} className="text-u-text3" />
        </>
      )}
    >
      {() => (
        <div className="p-1.5">
          <div className={POPOVER_HEADING}>Levels to search</div>
          {LEVEL_OPTIONS.map((option) => (
            <FilterCheckRow
              key={option.level}
              label={`${option.label} — ${option.hint}`}
              count={countOf(option.level)}
              checked={criteria.levels.includes(option.level)}
              onToggle={() => toggle(option.level)}
            />
          ))}
          <p className="mx-2 mb-1 mt-1 border-t border-u-border pt-2 text-note text-u-text3">
            Counts are people at these {companyCount} companies with your title words. Counting is free.
          </p>
        </div>
      )}
    </Popover>
  );
}

function TitlesField({
  criteria,
  onChange,
  counts,
}: {
  criteria: SearchCriteria;
  onChange: (criteria: SearchCriteria) => void;
  counts: SourcingCounts | null;
}) {
  const [typed, setTyped] = useState("");
  const [focused, setFocused] = useState(false);
  const words = [...criteria.functionTerms, ...criteria.standaloneTitles];
  const full = criteria.functionTerms.length >= MAX_TERMS;

  const add = (word: string) => {
    const cleaned = word.trim();
    if (!cleaned || words.some((existing) => existing.toLowerCase() === cleaned.toLowerCase())) return;
    const suggestions = criteria.suggestions.filter((suggested) => suggested.toLowerCase() !== cleaned.toLowerCase());
    if (STANDALONE.test(cleaned)) {
      onChange({ ...criteria, standaloneTitles: [...criteria.standaloneTitles, cleaned], suggestions });
    } else if (!full) {
      onChange({ ...criteria, functionTerms: [...criteria.functionTerms, cleaned], suggestions });
    }
  };
  const remove = (word: string) =>
    onChange({
      ...criteria,
      functionTerms: criteria.functionTerms.filter((term) => term !== word),
      standaloneTitles: criteria.standaloneTitles.filter((title) => title !== word),
    });
  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      add(typed);
      setTyped("");
    } else if (event.key === "Backspace" && typed === "" && words.length > 0) {
      remove(words[words.length - 1]);
    }
  };
  const countOf = (word: string) => counts?.words.find((count) => count.word === word)?.count;

  return (
    <div
      className={cn(
        "relative flex min-h-8 min-w-[280px] flex-1 flex-wrap items-center gap-1.5 rounded-md border bg-u-surface px-2 py-1",
        focused ? "border-u-accent ring-2 ring-u-accent-ring" : "border-u-border-strong",
      )}
    >
      <span className="pe-0.5 text-note font-medium text-u-text3">Titles</span>
      {words.map((word) => (
        <Pill key={word} onRemove={() => remove(word)} label={word} />
      ))}
      <input
        value={typed}
        onChange={(event) => setTyped(event.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => setFocused(true)}
        onBlur={() => window.setTimeout(() => setFocused(false), 150)}
        placeholder={full ? "12 words at most" : "Add a title word…"}
        aria-label="Add a title word"
        className="min-w-[110px] flex-1 bg-transparent text-note outline-none placeholder:text-u-text3"
      />
      {focused && criteria.suggestions.length > 0 && (
        <div className="absolute left-0 top-full z-20 mt-1.5 w-[440px] max-w-[90vw] rounded-lg border border-u-border-strong bg-u-surface p-3 shadow-u-e3">
          <div className="mb-1.5 text-meta font-semibold uppercase tracking-[.06em] text-u-text3">
            Suggested for this role
          </div>
          <div className="flex flex-wrap gap-1.5">
            {criteria.suggestions.map((word) => (
              <button
                key={word}
                type="button"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => add(word)}
                className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-u-border-strong px-2.5 py-[3px] text-note font-medium hover:bg-u-raised"
              >
                <Icon d={ICONS.plus} size={11} className="text-u-text3" />
                {word}
                {countOf(word) !== undefined && (
                  <span className="text-meta font-semibold tabular-nums text-u-text3">{countOf(word)}</span>
                )}
              </button>
            ))}
          </div>
          <p className="mt-2 border-t border-u-border pt-2 text-note text-u-text3">
            Type any other word and press Enter to add it. Each count is how many people at these companies hold that
            word and one of your levels in their current title.
          </p>
        </div>
      )}
    </div>
  );
}

function LeaveOutField({
  criteria,
  onChange,
}: {
  criteria: SearchCriteria;
  onChange: (criteria: SearchCriteria) => void;
}) {
  return (
    <WordListField
      label="Leave out"
      summary={criteria.excludedWords.length === 0 ? "Nothing" : criteria.excludedWords.join(", ")}
      words={criteria.excludedWords}
      max={MAX_EXCLUDED}
      placeholder="A word that marks support to the role…"
      onChange={(excludedWords) => onChange({ ...criteria, excludedWords })}
    />
  );
}

function WhereField({
  criteria,
  onChange,
  countries,
  countryName,
}: {
  criteria: SearchCriteria;
  onChange: (criteria: SearchCriteria) => void;
  countries: Country[];
  countryName: (code: string) => string;
}) {
  const summary =
    criteria.countryCodes.length === 0
      ? "Anywhere"
      : criteria.countryCodes.length <= 2
        ? criteria.countryCodes.map(countryName).join(", ")
        : `${countryName(criteria.countryCodes[0])} + ${criteria.countryCodes.length - 1} more`;
  return (
    <Popover
      width={320}
      hasPopup="dialog"
      label="Where people may live"
      triggerClassName={FIELD}
      trigger={() => (
        <>
          <span className="text-u-text3">Where</span>
          <span className="text-u-text">{summary}</span>
          <Icon d={ICONS.chevronDown} size={12} className="text-u-text3" />
        </>
      )}
    >
      {() => (
        <div className="p-2.5">
          <div className="flex flex-wrap gap-1.5">
            {criteria.countryCodes.map((code) => (
              <Pill
                key={code}
                label={countryName(code)}
                onRemove={() =>
                  onChange({ ...criteria, countryCodes: criteria.countryCodes.filter((kept) => kept !== code) })
                }
              />
            ))}
          </div>
          <select
            aria-label="Add a country"
            value=""
            onChange={(event) =>
              event.target.value &&
              onChange({ ...criteria, countryCodes: [...criteria.countryCodes, event.target.value] })
            }
            className="mt-2 h-8 w-full rounded-md border border-u-border-strong bg-u-surface px-2 text-note"
          >
            <option value="">Add a country…</option>
            {countries
              .filter((country) => !criteria.countryCodes.includes(country.code))
              .map((country) => (
                <option key={country.code} value={country.code}>
                  {country.name}
                </option>
              ))}
          </select>
          <p className="mt-2 text-note text-u-text3">
            Each company&apos;s own country is searched too. With no country, people are searched anywhere.
          </p>
        </div>
      )}
    </Popover>
  );
}

function CompaniesField({
  companies,
  included,
  isIncluded,
  onToggle,
  onAll,
}: {
  companies: CompanyCount[];
  included: number;
  isIncluded: (companyId: string) => boolean;
  onToggle: (companyId: string) => void;
  onAll: (included: boolean) => void;
}) {
  return (
    <Popover
      width={420}
      hasPopup="listbox"
      label="Companies to search"
      triggerClassName={FIELD}
      trigger={() => (
        <>
          <Icon d={ICONS.building} size={13} className="text-u-text3" />
          <span className="text-u-text">
            {included} of {companies.length} {companies.length === 1 ? "company" : "companies"}
          </span>
          <Icon d={ICONS.chevronDown} size={12} className="text-u-text3" />
        </>
      )}
    >
      {() => (
        <div className="p-1.5">
          <div className={cn(POPOVER_HEADING, "flex items-center")}>
            <span className="flex-1">Companies</span>
            <button type="button" onClick={() => onAll(true)} className="normal-case tracking-normal text-u-accent">
              All
            </button>
            <span className="px-1">·</span>
            <button type="button" onClick={() => onAll(false)} className="normal-case tracking-normal text-u-accent">
              None
            </button>
          </div>
          {companies.map((company) =>
            company.searchable ? (
              <FilterCheckRow
                key={company.triageCompanyId}
                label={`${company.companyName} — ${company.match} match · ${company.toBuy} to buy`}
                checked={isIncluded(company.triageCompanyId)}
                onToggle={() => onToggle(company.triageCompanyId)}
              />
            ) : (
              <p key={company.triageCompanyId} className="px-2 py-1.5 text-note text-u-text3">
                {company.companyName} <span className="text-u-signal">— no LinkedIn page; add it with Edit company</span>
              </p>
            ),
          )}
        </div>
      )}
    </Popover>
  );
}

function WordListField({
  label,
  summary,
  words,
  max,
  placeholder,
  onChange,
}: {
  label: string;
  summary: string;
  words: string[];
  max: number;
  placeholder: string;
  onChange: (words: string[]) => void;
}) {
  const [typed, setTyped] = useState("");
  return (
    <Popover
      width={320}
      hasPopup="dialog"
      label={label}
      triggerClassName={FIELD}
      trigger={() => (
        <>
          <span className="text-u-text3">{label}</span>
          <span className="max-w-[200px] truncate text-u-text">{summary}</span>
          <Icon d={ICONS.chevronDown} size={12} className="text-u-text3" />
        </>
      )}
    >
      {() => (
        <div className="p-2.5">
          <div className="flex flex-wrap gap-1.5">
            {words.map((word) => (
              <Pill key={word} label={word} onRemove={() => onChange(words.filter((kept) => kept !== word))} />
            ))}
          </div>
          {words.length < max && (
            <input
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== "Enter") return;
                event.preventDefault();
                const word = typed.trim();
                if (word && !words.some((kept) => kept.toLowerCase() === word.toLowerCase())) {
                  onChange([...words, word]);
                }
                setTyped("");
              }}
              placeholder={placeholder}
              aria-label={`Add a word to ${label.toLowerCase()}`}
              className="mt-2 h-8 w-full rounded-md border border-u-border-strong bg-u-surface px-2 text-note outline-none"
            />
          )}
        </div>
      )}
    </Popover>
  );
}

function Pill({ label, onRemove }: { label: ReactNode; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1 rounded bg-u-accent-tint py-0.5 pe-1 ps-2 text-note font-medium text-u-text">
      {label}
      <button type="button" onClick={onRemove} aria-label={`Remove ${String(label)}`} className="text-u-text3">
        <Icon d={ICONS.close} size={11} />
      </button>
    </span>
  );
}
