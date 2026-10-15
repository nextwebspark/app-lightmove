import { cn } from "../../../lib/cn";
import type { NationalityReading } from "../api/types";
import { AiInferredBadge } from "./CandidateFieldGroups";

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
    <div className="mt-3 rounded-u-chip border border-u-inferred/40 bg-u-inferred-tint px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-2">
        <AiInferredBadge />
        {isUnknown ? (
          <span className="text-note text-u-text2">AI couldn't tell this executive's nationality.</span>
        ) : (
          <>
            <span className="text-note text-u-text2">
              AI suggests nationality <strong className="font-semibold text-u-text">{reading.category}</strong>{" "}
              <span className="font-mono text-meta text-u-text3">({reading.confidence} confidence)</span>
            </span>
            <button
              type="button"
              onClick={() => onAccept(reading.category)}
              disabled={isAccepting}
              className="ms-auto rounded-md border border-u-inferred/40 bg-u-surface px-2 py-0.5 font-mono text-meta font-semibold text-u-inferred transition hover:border-u-inferred disabled:opacity-60"
            >
              {isAccepting ? "Saving…" : "Accept"}
            </button>
          </>
        )}
      </div>
      {(reading.evidenceFor.length > 0 || reading.evidenceAgainst.length > 0) && (
        <details className="mt-1.5">
          <summary className="cursor-pointer font-mono text-meta text-u-text3">Why</summary>
          <PointList points={reading.evidenceFor} tone="positive" />
          <PointList points={reading.evidenceAgainst} tone="negative" />
        </details>
      )}
    </div>
  );
}

function PointList({ points, tone }: { points: readonly string[]; tone: "positive" | "negative" }) {
  if (points.length === 0) return null;
  const isPositive = tone === "positive";
  return (
    <ul aria-label={isPositive ? "Evidence for" : "Evidence against"} className="mt-2 space-y-1">
      {points.map((point) => (
        <li key={point} className="flex gap-1.5 text-note text-u-text2">
          <span
            aria-hidden
            className={cn("w-3 flex-none text-center font-mono font-bold", isPositive ? "text-u-direct" : "text-u-offlimits")}
          >
            {isPositive ? "+" : "–"}
          </span>
          <span>{point}</span>
        </li>
      ))}
    </ul>
  );
}
