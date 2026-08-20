import type { ScopeCaveats } from "../api/types";

/**
 * Where the report's source could not answer the whole mandate scope. It sits directly under the
 * headline figures because that is what it qualifies: each line is the difference between a number
 * being low and a number being partly blind, and a reader cannot tell those apart from the bars.
 *
 * <p>Renders nothing when there is nothing to say — a permanent disclaimer is furniture, and stops
 * being read long before the one case that mattered.
 */
export function ScopeCaveatsNotice({ caveats }: { caveats: ScopeCaveats }) {
  const lines: string[] = [];

  if (caveats.revenueBandExcludesUnknown) {
    lines.push(
      "A revenue band is part of this scope, and companies with no revenue figure are excluded from the counts — most of this source carries none.",
    );
  }

  if (lines.length === 0) {
    return null;
  }

  return (
    <div className="mt-3.5 rounded-[10px] border border-dashed border-line bg-panel2 px-[18px] py-4">
      <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        What these figures do not cover
      </div>
      <ul className="mt-2 flex flex-col gap-1.5">
        {lines.map((line) => (
          <li key={line} className="max-w-[680px] text-[13px] leading-[1.6] text-text2">
            {line}
          </li>
        ))}
      </ul>
    </div>
  );
}
