import { NavLink } from "react-router-dom";
import { cn } from "../../../lib/cn";
import { TRIAGE_STAGES } from "../lib/triageStages";
import type { TriageCounts } from "../api/types";

/**
 * The three stages, as links rather than tabs.
 *
 * <p>They are links because each stage is its own page and its own URL — a consultant sends a
 * colleague the shortlist, not "the Companies screen, then click the second tab". The sidebar carries
 * the same three destinations; this repeats them beside the grid because that is where a move is
 * made, and the count changing is the confirmation the move happened.
 */
export function TriageStageSwitcher({
  projectId,
  counts,
}: {
  projectId: string;
  /** Undefined while the first page is still loading — a dash, never a zero we cannot vouch for. */
  counts: TriageCounts | undefined;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {TRIAGE_STAGES.map((stage) => (
        <NavLink
          key={stage.status}
          to={`/projects/${projectId}/companies/${stage.slug}`}
          className={({ isActive }) =>
            cn(
              "inline-flex items-center gap-2 rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-text",
              isActive ? "border-amber bg-amber-dim text-amber" : "border-line text-text2",
            )
          }
        >
          {stage.label}
          <span className="font-mono text-[10.5px] text-text3">
            {counts ? counts[stage.status] : "—"}
          </span>
        </NavLink>
      ))}
    </div>
  );
}
