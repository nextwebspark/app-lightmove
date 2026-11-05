import { useId, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { FilterCheckRow } from "../../../components/ui/FilterCheckRow";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { Country } from "../../../lib/countries";
import type { CompanyCount, SavedCriteria, SearchCriteria, SearchLevel, SourcingCounts } from "../api/sourcingApi";
import { LEVEL_OPTIONS, levelsSummary } from "../lib/searchLevels";
import { companiesOf } from "../lib/sourcingSummary";

const FIELD =
  "flex h-8 w-full min-w-0 items-center gap-1.5 rounded-md border border-u-border-strong bg-u-surface px-2.5 text-note font-medium hover:bg-u-raised";
const POPOVER_HEADING = "flex items-center px-1 pb-1.5 text-meta font-semibold uppercase tracking-[.06em] text-u-text3";
const QUIET_BUTTON =
  "inline-flex h-7 flex-none items-center gap-1.5 rounded-md border border-u-border-strong bg-u-surface px-2.5 text-note font-medium text-u-text hover:bg-u-raised";
const MAX_EXCLUDED = 4;
const MAX_TERMS = 12;

/** A C-suite abbreviation ("CIO") is a whole title on its own; every other word is a title word. */
const STANDALONE = /^C[A-Z]{1,3}O$/;

/**
 * The search, drafted from the brief: one line saying what it looks for, opened on Edit search into its fields —
 * levels and title words with free counts, the words left out, where people may live, which companies, and how
 * many profiles a company may buy, each a dropdown of one shape. Search is the only press that spends.
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
  const [expanded, setExpanded] = useState(false);
  const fieldsId = useId();
  const included = companies.filter((company) => isIncluded(company.triageCompanyId));
  const countLine = !criteria
    ? "Reading the brief…"
    : counting && !counts
      ? "Counting…"
      : counts?.counted
        ? `≈ ${sum(included, "match")} match · ${sum(included, "toBuy")} to buy · ${sum(included, "onFile")} already on file`
        : "";
  const summary = criteria
    ? [
        levelsSummary(criteria.levels),
        listed(titleWordsOf(criteria), "No title words", 3),
        whereOf(criteria, countryName),
        `${included.length} of ${companiesOf(companies.length)}`,
        `${criteria.perCompany} per company`,
      ].join(" · ")
    : null;

  return (
    <div className="flex-none px-[18px] pt-3">
      <div className="rounded-lg border border-u-border bg-u-sunken px-3 py-2">
        <div className="flex items-center gap-2.5">
          <Icon
            d={ICONS.sparkle}
            size={14}
            className={cn("flex-none", source === "AI" && !briefChanged ? "text-u-inferred" : "text-u-text3")}
          />
          <span className={cn("flex-none text-note font-semibold", briefChanged ? "text-u-signal" : "text-u-text")}>
            {noteOf(source, briefChanged, drafting)}
          </span>
          {!drafting && source !== null && (
            <button
              type="button"
              onClick={onReset}
              className="flex-none text-note font-medium text-u-accent hover:text-u-accent-hover"
            >
              Reset
            </button>
          )}
          <span className="min-w-0 flex-1 truncate text-note text-u-text2">{!expanded && summary}</span>
          {countLine && (
            <span className="hidden flex-none font-mono text-note text-u-text2 md:inline">{countLine}</span>
          )}
          <button
            type="button"
            aria-expanded={expanded}
            aria-controls={fieldsId}
            onClick={() => setExpanded(!expanded)}
            className={QUIET_BUTTON}
          >
            {expanded ? "Hide search" : "Edit search"}
            <Icon
              d={ICONS.chevronDown}
              size={12}
              className={cn("text-u-text3 transition-transform", expanded && "rotate-180")}
            />
          </button>
          <button
            type="button"
            onClick={onSearch}
            disabled={searchDisabled}
            className="inline-flex h-7 flex-none items-center gap-1.5 rounded-md bg-u-accent-solid px-3 text-note font-semibold text-white hover:bg-u-accent-solid-hover disabled:opacity-50"
          >
            <Icon d={ICONS.search} size={13} />
            {searchLabel}
          </button>
        </div>
        {expanded && (
          <div
            id={fieldsId}
            className="mt-2 grid grid-cols-1 gap-2 border-t border-u-border pt-2.5 sm:grid-cols-2 lg:grid-cols-3"
          >
            {criteria ? (
              <>
                <LevelField criteria={criteria} onChange={onChange} counts={counts} companyCount={included.length} />
                <TitlesField criteria={criteria} onChange={onChange} counts={counts} />
                <WordListField
                  label="Leave out"
                  words={criteria.excludedWords}
                  max={MAX_EXCLUDED}
                  placeholder="A word that marks support to the role…"
                  hint="A title holding one of these is never bought and reads as not fitting the role."
                  onChange={(excludedWords) => onChange({ ...criteria, excludedWords })}
                />
                <WhereField criteria={criteria} onChange={onChange} countries={countries} countryName={countryName} />
                <CompaniesField
                  companies={companies}
                  included={included.length}
                  isIncluded={isIncluded}
                  onToggle={onToggleCompany}
                  onAll={onAllCompanies}
                />
                <PerCompanyField
                  value={criteria.perCompany}
                  choices={perCompanyChoices}
                  onChange={(perCompany) => onChange({ ...criteria, perCompany })}
                />
              </>
            ) : (
              Array.from({ length: 6 }, (_, index) => (
                <span key={index} aria-hidden="true" className="h-8 animate-pulse rounded-md bg-u-border" />
              ))
            )}
            {countLine && <p className="font-mono text-note text-u-text2 md:hidden">{countLine}</p>}
          </div>
        )}
      </div>
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

/** "Technology, Engineering +13": the first few, then how many more. */
function listed(items: readonly string[], none: string, shown = 2): string {
  if (items.length === 0) return none;
  const head = items.slice(0, shown).join(", ");
  return items.length > shown ? `${head} +${items.length - shown}` : head;
}

const titleWordsOf = (criteria: SearchCriteria) => [...criteria.functionTerms, ...criteria.standaloneTitles];

function whereOf(criteria: SearchCriteria, countryName: (code: string) => string): string {
  return listed(criteria.countryCodes.map(countryName), "Anywhere", 1);
}

function FieldFace({ label, value, icon }: { label: string; value: ReactNode; icon?: ReactNode }) {
  return (
    <>
      {icon}
      <span className="flex-none text-u-text3">{label}</span>
      <span className="min-w-0 flex-1 truncate text-left text-u-text">{value}</span>
      <Icon d={ICONS.chevronDown} size={12} className="flex-none text-u-text3" />
    </>
  );
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
      trigger={() => <FieldFace label="Level" value={levelsSummary(criteria.levels)} />}
    >
      {() => (
        <div className="p-1">
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
          <p className="mx-1 mt-1.5 border-t border-u-border pt-2 text-note text-u-text3">
            Counts are people at {companiesOf(companyCount)} with your title words. Counting is free.
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
  const words = titleWordsOf(criteria);
  const full = criteria.functionTerms.length >= MAX_TERMS;
  const countOf = (word: string) => counts?.words.find((count) => count.word === word)?.count;

  const setWords = (next: string[]) => {
    const added = new Set(next.map((word) => word.toLowerCase()));
    onChange({
      ...criteria,
      functionTerms: next.filter((word) => !STANDALONE.test(word)).slice(0, MAX_TERMS),
      standaloneTitles: next.filter((word) => STANDALONE.test(word)),
      suggestions: criteria.suggestions.filter((suggested) => !added.has(suggested.toLowerCase())),
    });
  };
  const suggestions = criteria.suggestions.filter(
    (suggested) => !words.some((word) => word.toLowerCase() === suggested.toLowerCase()),
  );

  return (
    <Popover
      width={440}
      hasPopup="dialog"
      label="Title words"
      triggerClassName={FIELD}
      trigger={() => <FieldFace label="Titles" value={listed(words, "None yet")} />}
    >
      {() => (
        <div className="p-1">
          <div className={POPOVER_HEADING}>
            <span className="flex-1">Title words</span>
            {words.length > 0 && (
              <button
                type="button"
                onClick={() => setWords([])}
                className="normal-case tracking-normal text-u-accent hover:text-u-accent-hover"
              >
                Clear all
              </button>
            )}
          </div>
          <WordsEditor words={words} onChange={setWords} ariaLabel="Add a title word" placeholder="Add a title word…" />
          <p className="mt-1.5 px-1 text-note text-u-text3">
            {full
              ? `${MAX_TERMS} words at most, besides C-suite titles such as CFO.`
              : "Enter adds a word. A C-suite title such as CFO is searched on its own; any other word with a level."}
          </p>
          {suggestions.length > 0 && (
            <>
              <div className={cn(POPOVER_HEADING, "mt-2.5 border-t border-u-border pt-2.5")}>
                Suggested for this role
              </div>
              <div className="flex flex-wrap gap-1.5 px-1">
                {suggestions.map((word) => (
                  <button
                    key={word}
                    type="button"
                    disabled={full && !STANDALONE.test(word)}
                    onClick={() => setWords([...words, word])}
                    className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-u-border-strong px-2.5 py-0.5 text-note font-medium hover:bg-u-raised disabled:opacity-50"
                  >
                    <Icon d={ICONS.plus} size={11} className="text-u-text3" />
                    {word}
                    {countOf(word) !== undefined && (
                      <span className="text-meta font-semibold tabular-nums text-u-text3">{countOf(word)}</span>
                    )}
                  </button>
                ))}
              </div>
              <p className="mt-2 px-1 text-note text-u-text3">
                Each count is how many people at these companies hold that word and one of your levels in their current
                title.
              </p>
            </>
          )}
        </div>
      )}
    </Popover>
  );
}

function WordListField({
  label,
  words,
  max,
  placeholder,
  hint,
  onChange,
}: {
  label: string;
  words: string[];
  max: number;
  placeholder: string;
  hint: string;
  onChange: (words: string[]) => void;
}) {
  return (
    <Popover
      width={340}
      hasPopup="dialog"
      label={label}
      triggerClassName={FIELD}
      trigger={() => <FieldFace label={label} value={listed(words, "Nothing")} />}
    >
      {() => (
        <div className="p-1">
          <div className={POPOVER_HEADING}>
            <span className="flex-1">{label}</span>
            {words.length > 0 && (
              <button
                type="button"
                onClick={() => onChange([])}
                className="normal-case tracking-normal text-u-accent hover:text-u-accent-hover"
              >
                Clear all
              </button>
            )}
          </div>
          <WordsEditor
            words={words}
            onChange={onChange}
            full={words.length >= max}
            ariaLabel={`Add a word to ${label.toLowerCase()}`}
            placeholder={placeholder}
          />
          <p className="mt-1.5 px-1 text-note text-u-text3">
            {hint} {max} words at most.
          </p>
        </div>
      )}
    </Popover>
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
  return (
    <Popover
      width={340}
      hasPopup="dialog"
      label="Where people may live"
      triggerClassName={FIELD}
      trigger={() => <FieldFace label="Where" value={whereOf(criteria, countryName)} />}
    >
      {() => (
        <div className="p-1">
          <div className={POPOVER_HEADING}>
            <span className="flex-1">Where people may live</span>
            {criteria.countryCodes.length > 0 && (
              <button
                type="button"
                onClick={() => onChange({ ...criteria, countryCodes: [] })}
                className="normal-case tracking-normal text-u-accent hover:text-u-accent-hover"
              >
                Clear all
              </button>
            )}
          </div>
          <div className="flex flex-wrap gap-1.5 px-1">
            {criteria.countryCodes.map((code) => (
              <Pill
                key={code}
                label={countryName(code)}
                onRemove={() =>
                  onChange({
                    ...criteria,
                    countryCodes: criteria.countryCodes.filter((kept) => kept !== code),
                  })
                }
              />
            ))}
          </div>
          <select
            aria-label="Add a country"
            value=""
            onChange={(event) =>
              event.target.value &&
              onChange({
                ...criteria,
                countryCodes: [...criteria.countryCodes, event.target.value],
              })
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
          <p className="mt-2 px-1 text-note text-u-text3">
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
      trigger={() => <FieldFace label="Companies" value={`${included} of ${companies.length}`} />}
    >
      {() => (
        <div className="p-1">
          <div className={POPOVER_HEADING}>
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
                {company.companyName}{" "}
                <span className="text-u-signal">— no LinkedIn page; add it with Edit company</span>
              </p>
            ),
          )}
        </div>
      )}
    </Popover>
  );
}

function PerCompanyField({
  value,
  choices,
  onChange,
}: {
  value: number;
  choices: number[];
  onChange: (value: number) => void;
}) {
  return (
    <Popover
      width={300}
      hasPopup="listbox"
      label="Profiles bought per company"
      triggerClassName={FIELD}
      trigger={() => <FieldFace label="Per company" value={`${value} profiles`} />}
    >
      {(close) => (
        <div className="p-1">
          <div className={POPOVER_HEADING}>Profiles bought per company</div>
          <div role="listbox" aria-label="Profiles bought per company">
            {choices.map((choice) => (
              <button
                key={choice}
                type="button"
                role="option"
                aria-selected={choice === value}
                onClick={() => {
                  onChange(choice);
                  close();
                }}
                className="flex w-full items-center gap-2 rounded px-1 py-1.5 text-left text-body font-medium text-u-text hover:bg-u-raised"
              >
                <span className="flex w-4 flex-none justify-center">
                  {choice === value && <Icon d={ICONS.check} size={13} className="text-u-accent" />}
                </span>
                {choice} at most
              </button>
            ))}
          </div>
          <p className="mx-1 mt-1.5 border-t border-u-border pt-2 text-note text-u-text3">
            A credit a profile, the role&apos;s own level first. People already on file come back free.
          </p>
        </div>
      )}
    </Popover>
  );
}

/** The words as removable pills over a box that adds one on Enter — a repeat, in any case, is not added twice. */
function WordsEditor({
  words,
  onChange,
  full = false,
  ariaLabel,
  placeholder,
}: {
  words: string[];
  onChange: (words: string[]) => void;
  full?: boolean;
  ariaLabel: string;
  placeholder: string;
}) {
  const [typed, setTyped] = useState("");
  const add = () => {
    const word = typed.trim();
    setTyped("");
    if (word && !words.some((kept) => kept.toLowerCase() === word.toLowerCase())) onChange([...words, word]);
  };
  return (
    <div className="px-1">
      {words.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {words.map((word) => (
            <Pill key={word} label={word} onRemove={() => onChange(words.filter((kept) => kept !== word))} />
          ))}
        </div>
      )}
      {!full && (
        <input
          value={typed}
          onChange={(event) => setTyped(event.target.value)}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            add();
          }}
          aria-label={ariaLabel}
          placeholder={placeholder}
          className={cn(
            "h-8 w-full rounded-md border border-u-border-strong bg-u-surface px-2 text-note outline-none placeholder:text-u-text3 focus:border-u-accent",
            words.length > 0 && "mt-2",
          )}
        />
      )}
    </div>
  );
}

function Pill({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="inline-flex items-center gap-1 rounded bg-u-accent-tint py-0.5 pe-1 ps-2 text-note font-medium text-u-text">
      {label}
      <button type="button" onClick={onRemove} aria-label={`Remove ${label}`} className="text-u-text3">
        <Icon d={ICONS.close} size={11} />
      </button>
    </span>
  );
}
