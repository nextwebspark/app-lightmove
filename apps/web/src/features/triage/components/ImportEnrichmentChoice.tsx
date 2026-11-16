import { useQuery } from "@tanstack/react-query";
import { CheckBox } from "../../../components/ui/FilterCheckRow";
import { Spinner } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import * as importApi from "../api/importApi";
import type {
  ImportEnrichmentOffer,
  ImportEnrichmentProgress,
  ImportEnrichmentQueued,
  ImportPreview,
  ProposedColumnMapping,
} from "../api/importTypes";

const PROGRESS_EVERY_MS = 3000;

/** What enriching this file would look up, read off the columns as they are mapped now. */
export interface EnrichmentLinks {
  profiles: number;
  companies: number;
  total: number;
}

/** Where the box stands: why it cannot be ticked, if it cannot. */
export type EnrichmentBlock = "noLinks" | "tooMany" | null;

export function enrichmentLinksOf(preview: ImportPreview, mappings: readonly ProposedColumnMapping[]): EnrichmentLinks {
  const countOf = (field: string, pick: (index: number) => number) => {
    const mapped = mappings.find((mapping) => mapping.targetField === field);
    return mapped ? pick(mapped.index) : 0;
  };
  const column = (index: number) => preview.columns.find((candidate) => candidate.index === index);
  const profiles = preview.enrichment.profilesOffered
    ? countOf("candidateLinkedin", (index) => column(index)?.linkedinProfileCount ?? 0)
    : 0;
  const companies = preview.enrichment.companiesOffered
    ? countOf("companyLinkedin", (index) => column(index)?.linkedinCompanyCount ?? 0)
    : 0;
  return { profiles, companies, total: profiles + companies };
}

export function enrichmentBlockOf(offer: ImportEnrichmentOffer, links: EnrichmentLinks): EnrichmentBlock {
  if (links.total === 0) return "noLinks";
  if (links.total > offer.autoEnrichLimit) return "tooMany";
  return null;
}

/**
 * The mapping step's "Enrich from LinkedIn" box: ticked by default, and greyed out — unticked — when the file names
 * no LinkedIn link or more than one import enriches. Fills empty fields only; the file's own values are kept.
 */
export function ImportEnrichmentChoice({
  offer,
  links,
  checked,
  onChange,
  error,
}: {
  offer: ImportEnrichmentOffer;
  links: EnrichmentLinks;
  checked: boolean;
  onChange: (checked: boolean) => void;
  error?: string;
}) {
  const block = enrichmentBlockOf(offer, links);
  const isDisabled = block !== null;
  const isTicked = checked && !isDisabled;
  const isOverAllowance = isTicked && offer.allowanceLeft !== null && links.total > offer.allowanceLeft;

  return (
    <div className="rounded-lg border border-u-border px-3 py-2.5">
      <label className={cn("flex items-start gap-2.5", isDisabled ? "cursor-not-allowed" : "cursor-pointer")}>
        <span className="relative mt-px grid flex-none place-items-center">
          <input
            type="checkbox"
            checked={isTicked}
            disabled={isDisabled}
            onChange={(event) => onChange(event.target.checked)}
            className="peer absolute inset-0 z-10 cursor-pointer opacity-0 disabled:cursor-not-allowed"
          />
          <span
            className={cn(
              "rounded peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-u-accent",
              isDisabled && "opacity-50",
            )}
          >
            <CheckBox checked={isTicked} />
          </span>
        </span>
        <span className={cn("flex min-w-0 flex-col gap-0.5", isDisabled && "opacity-60")}>
          <span className="font-sans text-body text-u-text">I'm okay with enriching people and companies from LinkedIn</span>
          <span className="font-mono text-meta text-u-text3">{hintOf(block, links, offer)}</span>
        </span>
      </label>
      {isOverAllowance && !error && (
        <p className="ml-6.5 mt-1.5 font-mono text-meta text-u-offlimits">
          Only {offer.allowanceLeft} lookups are left this month — untick enrichment or import fewer rows.
        </p>
      )}
      {error && (
        <p role="alert" className="ml-6.5 mt-1.5 font-mono text-meta text-u-offlimits">
          {error}
        </p>
      )}
    </div>
  );
}

function hintOf(block: EnrichmentBlock, links: EnrichmentLinks, offer: ImportEnrichmentOffer): string {
  if (block === "noLinks") return "Map a LinkedIn column to enrich.";
  if (block === "tooMany") {
    return `Files with more than ${offer.autoEnrichLimit} LinkedIn links aren't enriched automatically.`;
  }
  return `Fills empty fields only — what your file says is kept. Looks up ${lookupsOf(links)}. Your workspace is responsible for telling these people their profile was looked up.`;
}

function lookupsOf(links: EnrichmentLinks): string {
  const parts = [
    links.profiles > 0 ? `${links.profiles} ${links.profiles === 1 ? "profile" : "profiles"}` : null,
    links.companies > 0 ? `${links.companies} ${links.companies === 1 ? "company" : "companies"}` : null,
  ].filter((part): part is string => part !== null);
  return `up to ${parts.join(" and ")}`;
}

/** The done step's line while an import's enrichment runs; the grid fills in behind the dialog as each lands. */
export function ImportEnrichmentProgressLine({
  projectId,
  queued,
}: {
  projectId: string;
  queued: ImportEnrichmentQueued;
}) {
  const progress = useQuery({
    queryKey: ["import-enrichment", projectId, queued.runId],
    queryFn: () => importApi.enrichmentProgress(projectId, queued.runId),
    refetchInterval: (query) => (query.state.data?.done ? false : PROGRESS_EVERY_MS),
  });

  if (progress.isError) {
    return (
      <p className="rounded-lg bg-u-accent-tint px-3 py-2 font-sans text-note text-u-accent">
        {queuedLine(queued)} The grid updates as each one lands.
      </p>
    );
  }
  const data = progress.data;
  return (
    <p className="flex items-center gap-2 rounded-lg bg-u-accent-tint px-3 py-2 font-sans text-note text-u-accent">
      {data && !data.done && <Spinner />}
      <span>{data?.done ? finishedLine(data) : runningLine(queued, data)}</span>
    </p>
  );
}

function queuedLine(queued: ImportEnrichmentQueued): string {
  const parts = [
    queued.peopleQueued > 0 ? `${queued.peopleQueued} ${queued.peopleQueued === 1 ? "person" : "people"}` : null,
    queued.companiesQueued > 0
      ? `${queued.companiesQueued} ${queued.companiesQueued === 1 ? "company" : "companies"}`
      : null,
  ].filter((part): part is string => part !== null);
  return `Enriching ${parts.join(" and ")} from LinkedIn.`;
}

function runningLine(queued: ImportEnrichmentQueued, progress: ImportEnrichmentProgress | undefined): string {
  const counted = progress ? ` ${progress.total - progress.pending} of ${progress.total} looked up.` : "";
  return `${queuedLine(queued)}${counted} The grid updates as each one lands.`;
}

function finishedLine(progress: ImportEnrichmentProgress): string {
  const enriched = progress.researched + progress.alreadyResearched;
  const parts = [`${enriched} enriched`];
  if (progress.notFound > 0) parts.push(`${progress.notFound} not found on LinkedIn`);
  const skipped = progress.refused + progress.unavailable + progress.failed;
  if (skipped > 0) parts.push(`${skipped} skipped`);
  return `Enrichment finished: ${parts.join(", ")}.`;
}
