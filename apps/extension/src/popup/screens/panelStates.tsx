import { workspaceOrigin } from "../../workspaceOrigin";
import type { CaptureRefusal } from "../lib/captureRefusal";

/** While the position is asked whether it already holds this page: the record's shape, shimmering. */
export function CheckingPosition() {
  return (
    <div role="status" className="flex min-h-0 flex-1 flex-col gap-3 p-3.5">
      <span className="flex items-center gap-2 text-[11.5px] text-u-text3">
        <span className="size-3 animate-spin rounded-full border-2 border-u-border-strong border-t-u-accent-solid" />
        Checking whether this position already has them…
      </span>
      <div className="flex items-center gap-3 rounded-[10px] border border-u-border bg-u-raised p-3">
        <span className="size-11 animate-pulse rounded-full bg-u-surface" />
        <span className="flex flex-1 flex-col gap-2">
          <span className="h-3 w-3/5 animate-pulse rounded bg-u-surface" />
          <span className="h-2.5 w-4/5 animate-pulse rounded bg-u-surface" />
        </span>
      </div>
      <span className="h-9 animate-pulse rounded-[8px] bg-u-raised" />
      <span className="h-28 animate-pulse rounded-[8px] bg-u-raised" />
    </div>
  );
}

const REFUSAL_COPY: Record<string, string> = {
  CANDIDATE_ALREADY_MAPPED:
    "This position already has someone matching this profile — by their email, or their name at the same employer.",
  TRIAGE_COMPANY_ALREADY_HELD: "This position already holds a company by that name.",
  PERSON_SUPPRESSED: "This person is on your workspace's suppression list, so they can't be added.",
  FORBIDDEN: "You are not on that position's team, so you cannot add to it.",
  PIPELINE_STATUS_RETIRED: "Your workspace retired that status. Choose another one.",
  TRIAGE_COMPANY_HAS_EXECUTIVES:
    "Someone has been added at this company since, so it stays in the universe. Remove it in Uncava if it should go.",
  CONFLICT: "This record changed while you were undoing, probably as its research landed. Try Undo again.",
};

/** A refusal the consultant can act on, rather than a generic apology. */
export function RefusalNote({ refusal, projectId }: { refusal: CaptureRefusal; projectId?: string }) {
  const explanation = REFUSAL_COPY[refusal.code] ?? refusal.message;

  return (
    <div role="alert" className="rounded-[8px] border border-u-offlimits/30 bg-u-offlimits-tint px-2.5 py-2 text-[12px] leading-[1.5] text-u-offlimits">
      {explanation}
      {projectId && (
        <button
          type="button"
          onClick={() => void chrome.tabs.create({ url: `${workspaceOrigin}/projects/${projectId}/companies/universe` })}
          className="mt-1 block font-semibold hover:underline"
        >
          Open in Uncava ↗
        </button>
      )}
    </div>
  );
}
