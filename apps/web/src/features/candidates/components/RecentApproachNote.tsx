import { useState } from "react";
import { cn } from "../../../lib/cn";
import type { RecentApproach } from "../api/types";
import { approachLines, hasApproach } from "../lib/recentApproach";

/**
 * Recently approached: who on the team already reached this person, on any position, and where else they are in
 * process. Amber when it matters, a quiet line when it is only history. A warning — it never holds a send or an add.
 */
export function RecentApproachNote({
  approach,
  className,
}: {
  approach: RecentApproach | null | undefined;
  className?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  if (!hasApproach(approach)) return null;
  const lines = approachLines(approach);
  if (lines.length === 0) return null;
  const [lead, ...rest] = lines;

  if (approach.tier === "INFO") {
    return (
      <p role="note" aria-label="Approached before" className={cn("text-meta text-u-text3", className)}>
        {lead}
      </p>
    );
  }
  return (
    <div
      role="note"
      aria-label="Recently approached"
      className={cn("rounded-u-card border border-u-signal/35 bg-u-signal-tint px-3 py-2 text-note text-u-text2", className)}
    >
      <p className="flex flex-wrap items-baseline gap-x-2">
        <b className="font-semibold text-u-signal">Recently approached.</b>
        <span className="min-w-0">{lead}</span>
        {rest.length > 0 && (
          <button
            type="button"
            aria-expanded={isOpen}
            onClick={() => setIsOpen((open) => !open)}
            className="text-u-accent hover:underline"
          >
            {isOpen ? "Show less" : `+${rest.length} more`}
          </button>
        )}
      </p>
      {isOpen && (
        <ul className="mt-1 space-y-0.5">
          {rest.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
