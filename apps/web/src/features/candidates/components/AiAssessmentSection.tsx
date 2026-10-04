import { cn } from "../../../lib/cn";
import { formatInstantDate } from "../../../lib/format";
import type { CompetencyPanelAssessment, NationalityReading } from "../api/types";
import type { AiEnrichment } from "../lib/useAiEnrichment";
import { AiInferredBadge } from "./CandidateFieldGroups";

/**
 * The body of the profile's AI assessment fold: the model's summary, a 1–10 reading per competency
 * panel with its positives and negatives.
 */
export function AiAssessmentBody({ enrichment }: { enrichment: AiEnrichment }) {
  const { assessment } = enrichment;
  if (enrichment.isLoading) {
    return <p className="pb-4 font-mono text-[12.5px] text-u-text3">Loading…</p>;
  }
  if (enrichment.isError) {
    return <p className="pb-4 font-mono text-[12.5px] text-u-text3">The AI assessment could not be read.</p>;
  }
  const lastFailure = enrichment.lastFailedAt && !enrichment.isRunning && (
    <p className="font-mono text-[11.5px] text-u-offlimits">
      Last AI enrichment failed {formatInstantDate(enrichment.lastFailedAt)} — try again
    </p>
  );
  if (!assessment) {
    return (
      <div className="space-y-2 pb-4">
        {lastFailure}
        <p className="font-mono text-[12.5px] text-u-text3">
          {enrichment.isRunning
            ? "Reading the profile and scoring against the brief…"
            : "Not assessed yet. AI deep enrich reads the profile and scores this executive against the brief's competencies."}
        </p>
      </div>
    );
  }
  return (
    <div className="space-y-4 pb-4">
      {lastFailure}
      <div className="flex items-center gap-2 font-mono text-[11px] text-u-text3">
        <AiInferredBadge />
        <span>Assessed {formatInstantDate(assessment.assessedAt)} — a proposal to review, not a finding</span>
      </div>
      {assessment.summary && <p className="text-[13px] leading-relaxed text-u-text">{assessment.summary}</p>}
      <div className="grid gap-3 md:grid-cols-2">
        <PanelCard title="Technical" panel={assessment.technical} />
        <PanelCard title="Behavioural" panel={assessment.behavioural} />
      </div>
    </div>
  );
}

/**
 * What the nationality classifier read while the field is still empty: a group to accept with one
 * click, or that it could not tell. A high reading has already filled the field, so what shows here is
 * a medium or low one — or a high one a researcher has since cleared, which is still only a suggestion.
 * The evidence both ways sits behind a disclosure, since it reasons about a person's origin.
 */
export function NationalitySuggestion({
  reading,
  onAccept,
  isAccepting,
}: {
  reading: NationalityReading;
  onAccept: (group: string) => void;
  isAccepting: boolean;
}) {
  const isUnknown = reading.category === "Unknown";
  return (
    <div className="mt-3 rounded-[10px] border border-u-inferred/40 bg-u-inferred-tint px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-2">
        <AiInferredBadge />
        {isUnknown ? (
          <span className="text-[12.5px] text-u-text2">AI couldn't tell this executive's nationality.</span>
        ) : (
          <>
            <span className="text-[12.5px] text-u-text2">
              AI suggests nationality <strong className="font-semibold text-u-text">{reading.category}</strong>{" "}
              <span className="font-mono text-[11px] text-u-text3">({reading.confidence} confidence)</span>
            </span>
            <button
              type="button"
              onClick={() => onAccept(reading.category)}
              disabled={isAccepting}
              className="ms-auto rounded-md border border-u-inferred/40 bg-u-surface px-2 py-0.5 font-mono text-[11.5px] font-semibold text-u-inferred transition hover:border-u-inferred disabled:opacity-60"
            >
              {isAccepting ? "Saving…" : "Accept"}
            </button>
          </>
        )}
      </div>
      {(reading.evidenceFor.length > 0 || reading.evidenceAgainst.length > 0) && (
        <details className="mt-1.5">
          <summary className="cursor-pointer font-mono text-[11px] text-u-text3">Why</summary>
          <PointList points={reading.evidenceFor} tone="positive" />
          <PointList points={reading.evidenceAgainst} tone="negative" />
        </details>
      )}
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
          <span aria-hidden className={cn("w-3 flex-none text-center font-mono font-bold", isPositive ? "text-u-direct" : "text-u-offlimits")}>
            {isPositive ? "+" : "–"}
          </span>
          <span>{point}</span>
        </li>
      ))}
    </ul>
  );
}
