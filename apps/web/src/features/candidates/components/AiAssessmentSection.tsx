import { useState } from "react";
import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import type { AssessmentSourceLink, CompetencyPanelAssessment } from "../api/types";
import type { AiEnrichment } from "../lib/useAiEnrichment";
import { AiInferredBadge } from "./CandidateFieldGroups";

/**
 * The body of the profile's AI assessment fold: the model's summary, a 1–10 reading per competency
 * panel with its positives and negatives, and the web pages it relied on.
 */
export function AiAssessmentBody({ enrichment }: { enrichment: AiEnrichment }) {
  const { assessment } = enrichment;
  if (enrichment.isLoading) {
    return <p className="pb-4 font-mono text-[12.5px] text-u-text3">Loading…</p>;
  }
  if (enrichment.isError) {
    return <p className="pb-4 font-mono text-[12.5px] text-u-text3">The AI assessment could not be read.</p>;
  }
  if (!assessment) {
    return (
      <p className="pb-4 font-mono text-[12.5px] text-u-text3">
        {enrichment.isRunning
          ? "Searching the web and scoring against the brief…"
          : "Not assessed yet. AI deep enrich reads the profile, searches the web and scores this executive against the brief's competencies."}
      </p>
    );
  }
  return (
    <div className="space-y-4 pb-4">
      <div className="flex items-center gap-2 font-mono text-[11px] text-u-text3">
        <AiInferredBadge />
        <span>Assessed {formatInstantDate(assessment.assessedAt)} — a proposal to review, not a finding</span>
      </div>
      {assessment.summary && <p className="text-[13px] leading-relaxed text-u-text">{assessment.summary}</p>}
      <div className="grid gap-3 md:grid-cols-2">
        <PanelCard title="Technical" panel={assessment.technical} />
        <PanelCard title="Behavioural" panel={assessment.behavioural} />
      </div>
      <SourceList sources={assessment.sources} />
    </div>
  );
}

/** The fold's header action: runs the enrichment, and says so while it runs. */
export function AiEnrichButton({ enrichment }: { enrichment: AiEnrichment }) {
  return (
    <button
      type="button"
      onClick={enrichment.start}
      disabled={enrichment.isRunning}
      className="inline-flex items-center gap-1.5 rounded-md border border-u-inferred/40 bg-u-inferred-tint px-2 py-1 font-mono text-[11.5px] font-semibold text-u-inferred transition hover:border-u-inferred disabled:cursor-progress disabled:opacity-60"
    >
      <span aria-hidden>✦</span>
      {enrichment.isRunning ? "Enriching…" : "AI deep enrich"}
    </button>
  );
}

/** The folded header's one-liner: both scores at a glance. */
export function aiAssessmentSummary(enrichment: AiEnrichment): string | null {
  const { assessment } = enrichment;
  if (!assessment) return enrichment.isRunning ? "Enriching…" : null;
  const scoreOf = (panel: CompetencyPanelAssessment | null) => (panel?.score == null ? "—" : `${panel.score}/10`);
  return `Technical ${scoreOf(assessment.technical)} · Behavioural ${scoreOf(assessment.behavioural)}`;
}

function PanelCard({ title, panel }: { title: string; panel: CompetencyPanelAssessment | null }) {
  const score = panel?.score ?? null;
  return (
    <div className="rounded-[10px] border border-u-border bg-u-raised p-3">
      <div className="flex items-baseline justify-between">
        <span className="font-mono text-[10.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">{title}</span>
        <span className="font-sans text-lg font-semibold text-u-text">
          {score === null ? "—" : score}
          <span className="font-mono text-[11px] font-normal text-u-text3">/10</span>
        </span>
      </div>
      {score === null && (panel?.positives.length ?? 0) === 0 && (panel?.negatives.length ?? 0) === 0 ? (
        <p className="mt-2 font-mono text-[12px] text-u-text3">Not enough in the brief or the evidence to judge.</p>
      ) : (
        <>
          <PointList points={panel?.positives ?? []} tone="positive" />
          <PointList points={panel?.negatives ?? []} tone="negative" />
        </>
      )}
    </div>
  );
}

function PointList({ points, tone }: { points: readonly string[]; tone: "positive" | "negative" }) {
  if (points.length === 0) return null;
  const isPositive = tone === "positive";
  return (
    <ul aria-label={isPositive ? "Positives" : "Negatives"} className="mt-2 space-y-1">
      {points.map((point) => (
        <li key={point} className="flex gap-1.5 text-[12.5px] leading-snug text-u-text2">
          <span aria-hidden className={cn("font-mono font-bold", isPositive ? "text-u-direct" : "text-u-offlimits")}>
            {isPositive ? "+" : "–"}
          </span>
          <span>{point}</span>
        </li>
      ))}
    </ul>
  );
}

/** The pages behind the reading, two lines of them until the reader asks for the rest. */
function SourceList({ sources }: { sources: readonly AssessmentSourceLink[] }) {
  const [expanded, setExpanded] = useState(false);
  if (sources.length === 0) return null;
  return (
    <div>
      <div className="mb-1.5 font-mono text-[9.5px] font-semibold uppercase tracking-[0.08em] text-u-text3">
        Sources
      </div>
      <ul className={cn("text-[12.5px] leading-[1.6]", !expanded && "line-clamp-2")}>
        {sources.map((source) => (
          <li key={source.url} className="inline after:mx-1.5 after:text-u-text3 after:content-['·'] last:after:content-none">
            <a
              href={source.url}
              target="_blank"
              rel="noopener noreferrer"
              className="text-u-accent underline-offset-2 hover:underline"
            >
              {source.title ?? hostOf(source.url)}
            </a>
          </li>
        ))}
      </ul>
      {sources.length > 1 && (
        <button
          type="button"
          onClick={() => setExpanded((current) => !current)}
          className="mt-1 font-mono text-[11px] text-u-text3 transition hover:text-u-text"
        >
          {expanded ? "See less" : "See more"}
        </button>
      )}
    </div>
  );
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}
