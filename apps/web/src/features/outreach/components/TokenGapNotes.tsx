import type { TokenGaps } from "../lib/sequenceTokens";

/** A person's gaps as a chip in a list. Marked, never a hold: a run is many people at once (decision 8). */
export function TokenGapChip({ gaps }: { gaps: TokenGaps }) {
  if (gaps.blanks.length > 0) {
    return (
      <span className="flex-none rounded-full bg-u-offlimits-tint px-1.5 py-px font-mono text-eyebrow text-u-offlimits">
        {gaps.blanks.length} blank
      </span>
    );
  }
  if (gaps.fallbacks.length > 0) {
    return (
      <span className="flex-none rounded-full bg-u-raised px-1.5 py-px font-mono text-eyebrow text-u-text3">Fallback</span>
    );
  }
  return null;
}

/** What goes out blank for this person and where a fallback stands in, said under their email. */
export function TokenGapLine({ gaps, firstName }: { gaps: TokenGaps; firstName: string }) {
  if (gaps.blanks.length === 0 && gaps.fallbacks.length === 0) return null;
  const listed = (names: string[]) => names.map((name) => `{{${name}}}`).join(", ");
  return (
    <p className="mt-3 border-t border-u-border pt-2.5 font-mono text-meta text-u-text3">
      {gaps.blanks.length > 0 && (
        <span className="text-u-offlimits">
          Nothing on file for {firstName}: {listed(gaps.blanks)} {gaps.blanks.length === 1 ? "goes" : "go"} out blank in
          this sequence. Give {gaps.blanks.length === 1 ? "it" : "them"} a fallback in the sequence, or fill in their
          profile.{" "}
        </span>
      )}
      {gaps.fallbacks.length > 0 &&
        `${gaps.blanks.length > 0 ? "The" : `Nothing on file for ${firstName}: the`} fallback stands in for ${listed(gaps.fallbacks)}.`}
    </p>
  );
}
