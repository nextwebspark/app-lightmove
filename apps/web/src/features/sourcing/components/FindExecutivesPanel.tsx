import { useEffect, useMemo, useRef, useState } from "react";
import { FullscreenButton } from "../../../components/ui/FullscreenButton";
import { Modal } from "../../../components/ui/Modal";
import { cn } from "../../../lib/cn";
import { useCountries } from "../../../lib/countries";
import { useFullscreen } from "../../../lib/useFullscreen";
import type { CompanyCount, CompanySuggestions, SourcingRun, SuggestedPerson } from "../api/sourcingApi";
import { LEVEL_TABS } from "../lib/searchLevels";
import { companiesOf, peopleOf } from "../lib/sourcingSummary";
import { titleLevelOf } from "../lib/suggestionReasons";
import { useSearchCriteria } from "../lib/useSearchCriteria";
import { useSourcingCounts } from "../lib/useSourcingCounts";
import type { SourcingRunState } from "../lib/useSourcingRun";
import { useSuggestions } from "../lib/useSuggestions";
import { CompanyResults, type CompanyStatus } from "./CompanyResults";
import { ProfilePane } from "./ProfilePane";
import { SearchBar } from "./SearchBar";
import { SearchProgress } from "./SearchProgress";

const PRE_TICKED_PER_COMPANY = 3;
const MAX_ADD = 50;

interface Picked {
  triageCompanyId: string;
  linkedinSlug: string;
}

const keyOf = (triageCompanyId: string, linkedinSlug: string) => `${triageCompanyId}|${linkedinSlug.toLowerCase()}`;

/**
 * Find executives: the position's search, drafted from the brief and changed in place with free counts; the
 * search over the companies, company by company, with Stop; what each found, ranked with its reasons; and Add,
 * which files the people ticked. Opened from the toolbar with the ticked companies (or the first ones with nobody
 * mapped) and from a company's drawer with that company alone.
 */
export function FindExecutivesPanel({
  open,
  onClose,
  projectId,
  subtitle,
  scope,
  sourcing,
}: {
  open: boolean;
  onClose: () => void;
  projectId: string;
  /** The role, its level and its place, under the title. */
  subtitle: string;
  /** The companies the panel opened on; empty for the search's own default. */
  scope: readonly string[];
  sourcing: SourcingRunState;
}) {
  const [fullscreen, toggleFullscreen] = useFullscreen();
  const run = sourcing.run;
  const companyIds = useMemo(
    () => (scope.length > 0 ? scope : (run?.companies.map((company) => company.triageCompanyId) ?? [])),
    [scope, run],
  );
  const criteria = useSearchCriteria(projectId, companyIds, open);
  const { counts, counting } = useSourcingCounts(projectId, companyIds, open ? criteria.criteria : null);
  const suggestions = useSuggestions(projectId, open);
  const { countries } = useCountries();
  const countryName = (code: string) => countries.find((country) => country.code === code)?.name ?? code;

  const [excluded, setExcluded] = useState<ReadonlySet<string>>(new Set());
  const [levelTab, setLevelTab] = useState<string | null>(null);
  const [opened, setOpened] = useState<{ triageCompanyId: string; person: SuggestedPerson } | null>(null);
  const [ticked, setTicked] = useState<ReadonlyMap<string, Picked>>(new Map());
  const [retryWords, setRetryWords] = useState<ReadonlySet<string>>(new Set());
  const [searchOpen, setSearchOpen] = useState(false);

  const { markOpened } = sourcing;
  useEffect(() => {
    if (open) markOpened();
  }, [open, markOpened]);

  const companies: CompanyCount[] = counts?.companies ?? [];
  const isIncluded = (companyId: string) => !excluded.has(companyId);
  const searchIds = companies
    .filter((company) => company.searchable && isIncluded(company.triageCompanyId))
    .map((company) => company.triageCompanyId);
  const found = useMemo(
    () => new Map(suggestions.companies.map((company) => [company.triageCompanyId, company])),
    [suggestions.companies],
  );

  const preTicked = useRef(new Set<string>());
  useEffect(() => {
    const additions: [string, Picked][] = [];
    for (const company of suggestions.companies) {
      if (preTicked.current.has(company.runId + company.triageCompanyId)) continue;
      preTicked.current.add(company.runId + company.triageCompanyId);
      company.people
        .filter((person) => person.verdict === "FITS" && person.candidateId === null)
        .slice(0, PRE_TICKED_PER_COMPANY)
        .forEach((person) =>
          additions.push([
            keyOf(company.triageCompanyId, person.linkedinSlug),
            { triageCompanyId: company.triageCompanyId, linkedinSlug: person.linkedinSlug },
          ]),
        );
    }
    if (additions.length > 0) setTicked((current) => new Map([...current, ...additions]));
  }, [suggestions.companies]);

  const shown = companies.map((company) => ({
    company,
    status: statusOf(company, run, found.get(company.triageCompanyId)),
    found: found.get(company.triageCompanyId) ?? null,
  }));
  const people = shown.flatMap((entry) => entry.found?.people ?? []);
  const hasResults = shown.some((entry) => entry.found !== null) || sourcing.isRunning;
  const nobodyFits = shown.filter((entry) => entry.status === "searched" && entry.found?.result.fits === 0);
  const tryWords = (counts?.words ?? []).filter((word) => word.count > 0);
  const tabs = LEVEL_TABS.map((tab) => ({
    ...tab,
    count: people.filter((person) => person.verdict === "FITS" && titleLevelOf(person) === tab.key).length,
  })).filter((tab) => tab.count > 0);

  const toggle = (triageCompanyId: string, person: SuggestedPerson) =>
    setTicked((current) => {
      const next = new Map(current);
      const key = keyOf(triageCompanyId, person.linkedinSlug);
      if (next.has(key)) next.delete(key);
      else next.set(key, { triageCompanyId, linkedinSlug: person.linkedinSlug });
      return next;
    });
  const isTicked = (triageCompanyId: string, person: SuggestedPerson) =>
    person.candidateId === null && ticked.has(keyOf(triageCompanyId, person.linkedinSlug));
  const pickedCount = [...ticked.values()].filter((picked) => {
    const person = found.get(picked.triageCompanyId)?.people.find((one) => one.linkedinSlug === picked.linkedinSlug);
    return person !== undefined && person.candidateId === null;
  }).length;

  const search = async (ids: readonly string[]) => {
    await criteria.saveNow();
    sourcing.start(ids);
  };
  const addWord = (word: string) => {
    if (!criteria.criteria) return;
    criteria.update({
      ...criteria.criteria,
      functionTerms: [...criteria.criteria.functionTerms, word],
      suggestions: criteria.criteria.suggestions.filter((suggested) => suggested !== word),
    });
  };
  const retry = async () => {
    if (!criteria.criteria) return;
    criteria.update({
      ...criteria.criteria,
      functionTerms: [...criteria.criteria.functionTerms, ...retryWords],
      suggestions: criteria.criteria.suggestions.filter((suggested) => !retryWords.has(suggested)),
    });
    setRetryWords(new Set());
    await search(nobodyFits.map((entry) => entry.company.triageCompanyId));
  };
  const add = () => {
    const picked = [...ticked.values()].slice(0, MAX_ADD);
    suggestions.add(picked, {
      onSuccess: () =>
        setTicked((current) => {
          const next = new Map(current);
          picked.forEach((one) => next.delete(keyOf(one.triageCompanyId, one.linkedinSlug)));
          return next;
        }),
    });
  };

  const searchable =
    criteria.criteria !== null &&
    (criteria.criteria.functionTerms.length > 0 || criteria.criteria.standaloneTitles.length > 0);
  const searchLabel = sourcing.isRunning
    ? "Searching…"
    : hasResults
      ? "Search again"
      : `Search ${companiesOf(searchIds.length || companies.length)}`;

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Find executives"
      subtitle={subtitle}
      closeButton
      fills
      headerAside={<FullscreenButton active={fullscreen} onToggle={toggleFullscreen} />}
      className={cn(
        "h-[90dvh]",
        fullscreen ? "h-dvh max-h-none w-screen max-w-none rounded-none md:w-screen" : "md:w-[1080px]",
      )}
      footer={
        <div className="flex items-center gap-3">
          {pickedCount > 0 && <span className="text-body font-semibold">{pickedCount} selected</span>}
          <span className="font-mono text-note text-u-text3">
            {pickedCount > 0 ? "Added at In universe from the profiles already read" : "Tick people to add them"}
          </span>
          <span className="flex-1" />
          {pickedCount > 0 && (
            <button type="button" onClick={() => setTicked(new Map())} className="px-2.5 text-body text-u-text2">
              Clear
            </button>
          )}
          <button
            type="button"
            onClick={add}
            disabled={pickedCount === 0 || suggestions.adding}
            className="inline-flex h-[34px] items-center rounded-md bg-u-accent-solid px-3.5 text-body font-semibold text-white hover:bg-u-accent-solid-hover disabled:opacity-50"
          >
            {suggestions.adding ? "Adding…" : `Add ${pickedCount || ""} to In universe`.replace("  ", " ")}
          </button>
        </div>
      }
    >
      <div className="flex h-full min-h-0 flex-col">
        <button
          type="button"
          onClick={() => setSearchOpen(!searchOpen)}
          className="flex-none border-b border-u-border px-4 py-2 text-left text-note font-medium text-u-accent md:hidden"
        >
          {searchOpen ? "Hide the search" : "Edit search"}
        </button>
        <div className={cn(!searchOpen && "hidden md:block")}>
          <SearchBar
            criteria={criteria.criteria}
            onChange={criteria.update}
            source={criteria.source}
            briefChanged={criteria.briefChanged}
            drafting={criteria.drafting}
            onReset={criteria.reset}
            counts={counts}
            counting={counting}
            companies={companies}
            isIncluded={isIncluded}
            onToggleCompany={(companyId) =>
              setExcluded((current) => {
                const next = new Set(current);
                if (next.has(companyId)) next.delete(companyId);
                else next.add(companyId);
                return next;
              })
            }
            onAllCompanies={(all) =>
              setExcluded(all ? new Set() : new Set(companies.map((company) => company.triageCompanyId)))
            }
            perCompanyChoices={sourcing.config?.perCompanyChoices ?? [3, 5, 10]}
            countries={countries}
            countryName={countryName}
            searchLabel={searchLabel}
            searchDisabled={!searchable || sourcing.isRunning || criteria.drafting || searchIds.length === 0}
            onSearch={() => void search(searchIds)}
          />
        </div>
        {run && sourcing.isRunning && (
          <SearchProgress run={run} onStop={sourcing.stop} stopping={sourcing.isStopping} />
        )}
        {hasResults && (
          <div className="flex flex-none items-end gap-0.5 border-b border-u-border px-[18px] pt-1.5">
            <LevelTab label="All" count={people.length} active={levelTab === null} onSelect={() => setLevelTab(null)} />
            {tabs.map((tab) => (
              <LevelTab
                key={tab.key}
                label={tab.label}
                count={tab.count}
                active={levelTab === tab.key}
                onSelect={() => setLevelTab(tab.key)}
              />
            ))}
            <span className="flex-1" />
            <span className="pb-2 font-mono text-note text-u-text3">
              {people.length} found · {people.filter((person) => person.verdict === "FITS").length} fit the role
            </span>
          </div>
        )}
        <div className="flex min-h-0 flex-1">
          <div className={cn("min-w-0 flex-1 overflow-y-auto pb-4", opened && "hidden md:block")}>
            {!hasResults && criteria.criteria === null && (
              <p className="px-[18px] pt-5 text-body text-u-text2">
                Reading the brief for the role&apos;s title, level and location…
              </p>
            )}
            {!hasResults && criteria.criteria !== null && (
              <Preview companies={companies} isIncluded={isIncluded} perCompany={criteria.criteria.perCompany} />
            )}
            {hasResults && nobodyFits.length > 0 && tryWords.length > 0 && !sourcing.isRunning && (
              <div className="mx-[18px] mt-3.5 flex flex-wrap items-center gap-2.5 rounded-lg border border-dashed border-u-border-strong px-3 py-2.5">
                <span className="text-body font-medium">
                  {companiesOf(nobodyFits.length)} found nobody who fits.
                </span>
                <span className="text-note text-u-text2">Try</span>
                {tryWords.map(({ word, count }) => (
                  <button
                    key={word}
                    type="button"
                    aria-pressed={retryWords.has(word)}
                    onClick={() =>
                      setRetryWords((current) => {
                        const next = new Set(current);
                        if (next.has(word)) next.delete(word);
                        else next.add(word);
                        return next;
                      })
                    }
                    className={cn(
                      "inline-flex items-center gap-1.5 rounded-full px-2.5 py-[3px] text-note font-medium",
                      retryWords.has(word)
                        ? "border border-u-accent bg-u-accent-tint"
                        : "border border-dashed border-u-border-strong",
                    )}
                  >
                    {word}
                    <span className="text-meta font-semibold tabular-nums text-u-text3">{count}</span>
                  </button>
                ))}
                <span className="flex-1" />
                <button
                  type="button"
                  onClick={() => void retry()}
                  disabled={retryWords.size === 0}
                  className="h-[30px] rounded-md bg-u-accent-solid px-3 text-note font-semibold text-white disabled:opacity-50"
                >
                  Retry {companiesOf(nobodyFits.length)}
                </button>
              </div>
            )}
            {hasResults &&
              shown.map(({ company, status, found: atCompany }) => (
                <CompanyResults
                  key={company.triageCompanyId}
                  companyName={company.companyName}
                  status={status}
                  found={atCompany}
                  levelTab={levelTab}
                  isTicked={(person) => isTicked(company.triageCompanyId, person)}
                  onToggle={(person) => toggle(company.triageCompanyId, person)}
                  onOpen={(person) => setOpened({ triageCompanyId: company.triageCompanyId, person })}
                  openSlug={opened?.person.linkedinSlug ?? null}
                  countryName={countryName}
                  tryWords={tryWords}
                  onTryWord={addWord}
                  onSearchAgain={() => void search([company.triageCompanyId])}
                />
              ))}
            {hasResults && run && (
              <p className="px-[18px] pt-3 font-mono text-note text-u-text3">
                Last search: {peopleOf(run.suggested)} fit · {run.profilesBought} profiles bought ·{" "}
                {run.profilesCached} read again free
              </p>
            )}
          </div>
          {opened && (
            <ProfilePane
              person={opened.person}
              ticked={isTicked(opened.triageCompanyId, opened.person)}
              onToggle={() => toggle(opened.triageCompanyId, opened.person)}
              onClose={() => setOpened(null)}
              countryName={countryName}
            />
          )}
        </div>
      </div>
    </Modal>
  );
}

/** Where a company stands: in the search running now, searched before, or not searched at all. */
function statusOf(company: CompanyCount, run: SourcingRun | null, found: CompanySuggestions | undefined): CompanyStatus {
  if (!company.searchable) return "noPage";
  const inRun = run?.companies.some((one) => one.triageCompanyId === company.triageCompanyId) ?? false;
  const outcome = run?.outcomes.find((one) => one.triageCompanyId === company.triageCompanyId);
  if (inRun && run && !outcome && (run.status === "QUEUED" || run.status === "RUNNING")) {
    const pending = run.companies.filter((one) => !run.outcomes.some((done) => done.triageCompanyId === one.triageCompanyId));
    return pending[0]?.triageCompanyId === company.triageCompanyId ? "searching" : "waiting";
  }
  if (outcome?.outcome === "FAILED") return "failed";
  if (outcome?.outcome === "STOPPED" || outcome?.outcome === "NOT_REACHED") return found ? "searched" : "stopped";
  return found ? "searched" : "unsearched";
}

function LevelTab({
  label,
  count,
  active,
  onSelect,
}: {
  label: string;
  count: number;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onSelect}
      className={cn(
        "inline-flex flex-none items-center gap-1.5 whitespace-nowrap border-b-2 px-2.5 pb-[9px] pt-2 text-note font-medium",
        active ? "border-u-accent text-u-text" : "border-transparent text-u-text2",
      )}
    >
      {label}
      <span
        className={cn(
          "rounded-full px-1.5 text-meta font-semibold tabular-nums",
          active ? "bg-u-accent-tint text-u-accent" : "bg-u-sunken text-u-text3",
        )}
      >
        {count}
      </span>
    </button>
  );
}

/** Before anything is searched: each company with what the search would reach there, counted free. */
function Preview({
  companies,
  isIncluded,
  perCompany,
}: {
  companies: CompanyCount[];
  isIncluded: (companyId: string) => boolean;
  perCompany: number;
}) {
  const searchable = companies.filter((company) => company.searchable && isIncluded(company.triageCompanyId));
  if (companies.length === 0) return null;
  return (
    <div className="px-[18px] pt-4">
      <div className="flex flex-wrap items-baseline gap-2.5">
        <span className="text-body font-semibold">
          Ready to search {searchable.length} of {companiesOf(companies.length)}
        </span>
        <span className="font-mono text-note text-u-text3">
          Counting and title checks are free · Search buys at most {perCompany} profiles a company
        </span>
      </div>
      <div className="mt-3 overflow-hidden rounded-lg border border-u-border">
        {companies.map((company, index) => (
          <div
            key={company.triageCompanyId}
            className={cn(
              "flex flex-wrap items-center gap-2.5 px-3 py-2.5",
              index > 0 && "border-t border-u-border",
              (!company.searchable || !isIncluded(company.triageCompanyId)) && "opacity-70",
            )}
          >
            <span className="w-[260px] text-body font-medium">{company.companyName}</span>
            {company.searchable ? (
              <span className="font-mono text-note text-u-text2">
                {company.match} match · {company.toBuy} to buy · {company.onFile} already on file
                {company.match === 0 && " — nobody matches yet; try the suggested words above"}
              </span>
            ) : (
              <span className="text-note text-u-signal">No LinkedIn page — add it with Edit company to search here</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
