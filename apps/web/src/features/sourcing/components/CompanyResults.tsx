import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { CompanySuggestions, SuggestedPerson } from "../api/sourcingApi";
import { titleLevelOf } from "../lib/suggestionReasons";
import { SuggestionRow } from "./SuggestionRow";

/** Where a company stands in the search on screen. */
export type CompanyStatus = "searching" | "waiting" | "searched" | "unsearched" | "noPage" | "failed" | "stopped";

const STATUS_NOTE: Partial<Record<CompanyStatus, string>> = {
  waiting: "Waiting",
  unsearched: "Not searched yet",
  noPage: "No LinkedIn page — add it with Edit company to search here",
  failed: "The search failed here — search again",
  stopped: "Not searched — the search was stopped",
};

/**
 * One company's results: the people who fit, best first, then — folded — those with no title on record and those
 * who don't fit, each still tickable. A company where nobody fits offers words to try and a search of it alone.
 */
export function CompanyResults({
  companyName,
  status,
  found,
  levelTab,
  isTicked,
  onToggle,
  onOpen,
  openSlug,
  countryName,
  tryWords,
  onTryWord,
  onSearchAgain,
}: {
  companyName: string;
  status: CompanyStatus;
  found: CompanySuggestions | null;
  levelTab: string | null;
  isTicked: (person: SuggestedPerson) => boolean;
  onToggle: (person: SuggestedPerson) => void;
  onOpen: (person: SuggestedPerson) => void;
  openSlug: string | null;
  countryName: (code: string) => string;
  tryWords: { word: string; count: number }[];
  onTryWord: (word: string) => void;
  onSearchAgain: () => void;
}) {
  const [howOpen, setHowOpen] = useState(false);
  const [noTitleOpen, setNoTitleOpen] = useState(false);
  const [notFitOpen, setNotFitOpen] = useState(false);
  const people = found?.people ?? [];
  const inTab = (person: SuggestedPerson) => levelTab === null || titleLevelOf(person) === levelTab;
  const fits = people.filter((person) => person.verdict === "FITS" && inTab(person));
  const noTitle = levelTab === null ? people.filter((person) => person.verdict === "NO_TITLE") : [];
  const notFit = levelTab === null ? people.filter((person) => person.verdict === "NOT_FIT") : [];
  const result = found?.result ?? null;
  const onPosition = people.filter((person) => person.candidateId !== null).length;
  const nobodyFits = status === "searched" && result !== null && result.fits === 0;
  if (levelTab !== null && fits.length === 0) return null;

  const row = (person: SuggestedPerson) => (
    <SuggestionRow
      key={person.linkedinSlug}
      person={person}
      ticked={isTicked(person)}
      onToggle={() => onToggle(person)}
      onOpen={() => onOpen(person)}
      highlighted={openSlug === person.linkedinSlug}
      countryName={countryName}
    />
  );

  return (
    <section aria-label={companyName} className="mx-[18px] mt-3.5 overflow-hidden rounded-lg border border-u-border">
      <div className="flex flex-wrap items-center gap-2.5 bg-u-sunken px-3 py-2.5">
        <span className="text-body font-semibold text-u-text">{companyName}</span>
        {result && status === "searched" && (
          <span className="font-mono text-note text-u-text3">
            {result.found} found · {result.fits} fit{onPosition > 0 ? ` · ${onPosition} on this position` : ""}
          </span>
        )}
        {status === "searching" && (
          <span className="inline-flex items-center gap-1.5 text-note font-medium text-u-text2">
            <span className="size-[11px] animate-spin rounded-full border-2 border-u-border border-t-u-accent" />
            Searching…
          </span>
        )}
        {STATUS_NOTE[status] && <span className="text-note font-medium text-u-text3">{STATUS_NOTE[status]}</span>}
        {nobodyFits && result.found === 0 && (
          <span className="rounded-full bg-u-signal-tint px-2.5 py-0.5 text-meta font-semibold text-u-signal">
            Nobody found
          </span>
        )}
        {found?.expired && <span className="text-note font-medium text-u-text3">Results expired — search again</span>}
        <span className="flex-1" />
        {result && status === "searched" && (
          <button
            type="button"
            aria-expanded={howOpen}
            onClick={() => setHowOpen(!howOpen)}
            className="inline-flex items-center gap-1 text-note font-medium text-u-text3 hover:text-u-text"
          >
            How we searched
            <Icon d={howOpen ? ICONS.chevronDown : ICONS.chevronRight} size={12} />
          </button>
        )}
      </div>
      {howOpen && result && (
        <div className="flex flex-col gap-1 border-t border-u-border py-2.5 pl-12 pr-3 font-mono text-note text-u-text2">
          <span>
            Live profiles — {result.matched ?? 0} match; {result.bought} bought, the role&apos;s own level first;{" "}
            {result.cached} already on file
          </span>
          <span>
            Profile index — {result.indexFound} here mention the function; {result.titleChecks} checked free;{" "}
            {result.noTitle} with no title on record
          </span>
        </div>
      )}
      {fits.map(row)}
      {nobodyFits && (
        <div className="border-t border-u-border py-3 pl-12 pr-3">
          <p className="text-note text-u-text2">
            {result.found === 0
              ? "Nobody here has these title words at these levels where the role is."
              : "Somebody was found, but nobody whose current title fits the role."}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {tryWords.length > 0 && <span className="text-note font-medium text-u-text3">Try</span>}
            {tryWords.map(({ word, count }) => (
              <button
                key={word}
                type="button"
                onClick={() => onTryWord(word)}
                className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-u-border-strong px-2.5 py-[3px] text-note font-medium hover:bg-u-raised"
              >
                <Icon d={ICONS.plus} size={11} />
                {word}
                <span className="text-meta font-semibold tabular-nums text-u-text3">{count}</span>
              </button>
            ))}
            <span className="flex-1" />
            <button
              type="button"
              onClick={onSearchAgain}
              className="h-7 rounded-md border border-u-border-strong px-2.5 text-note font-medium hover:bg-u-raised"
            >
              Search this company again
            </button>
          </div>
        </div>
      )}
      {(noTitle.length > 0 || notFit.length > 0) && (
        <div className="flex gap-[18px] border-t border-u-border py-2 pl-12 pr-3">
          {noTitle.length > 0 && (
            <Fold label={`Title not on record (${noTitle.length})`} open={noTitleOpen} onToggle={setNoTitleOpen} />
          )}
          {notFit.length > 0 && (
            <Fold label={`Doesn't fit the role (${notFit.length})`} open={notFitOpen} onToggle={setNotFitOpen} />
          )}
        </div>
      )}
      {noTitleOpen && noTitle.map(row)}
      {notFitOpen && notFit.map(row)}
    </section>
  );
}

function Fold({ label, open, onToggle }: { label: string; open: boolean; onToggle: (open: boolean) => void }) {
  return (
    <button
      type="button"
      aria-expanded={open}
      onClick={() => onToggle(!open)}
      className={cn("inline-flex items-center gap-1 text-note font-medium", open ? "text-u-text" : "text-u-text2")}
    >
      <Icon d={open ? ICONS.chevronDown : ICONS.chevronRight} size={12} className="text-u-text3" />
      {label}
    </button>
  );
}
