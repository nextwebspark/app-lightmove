import { UncavaRailNav } from "../../../components/layout/UncavaRailNav";
import type { SaveStatus } from "../../../lib/useAutosave";
import type { Position } from "../api/types";
import { POSITION_STEPS, STEP_PARAM, type StepKey } from "../lib/steps";
import { BriefButton } from "./BriefFields";

/**
 * The rail beside the brief: the five steps, then the two acts at a viewport-height foot (a strip and a row below `lg`).
 * A published brief nobody reopened offers Edit position instead, and no draft to save.
 */
export function BriefRail({
  position,
  activeKey,
  saveStatus,
  publishing,
  readBack,
  onPublish,
  onEditPosition,
  onSaveDraft,
  onRetrySave,
}: {
  position: Position;
  activeKey: StepKey;
  saveStatus: SaveStatus;
  publishing: boolean;
  /** Published and not reopened: the brief reads back and the rail offers the way in. */
  readBack: boolean;
  onPublish: () => void;
  onEditPosition: () => void;
  onSaveDraft: () => void;
  onRetrySave: () => void;
}) {
  const published = Boolean(position.publication.publishedAt);
  const stepLinks = POSITION_STEPS.map((step) => ({ key: step.key, label: step.name, icon: step.icon }));

  return (
    <aside className="flex-none border-b border-u-border lg:w-[258px] lg:border-b-0 lg:border-r">
      <div className="flex flex-col gap-3 px-4 py-3.5 lg:sticky lg:top-0 lg:h-[calc(100dvh-62px)] lg:gap-0 lg:overflow-y-auto lg:px-[22px] lg:py-[30px]">
        <UncavaRailNav label="Brief steps" param={STEP_PARAM} items={stepLinks} activeKey={activeKey} />

        <div className="flex flex-wrap items-center gap-2 lg:mt-auto lg:flex-col lg:items-stretch lg:pt-6">
          <SaveStatusLine status={saveStatus} onRetry={onRetrySave} />
          <BriefButton
            onClick={readBack ? onEditPosition : onPublish}
            loading={publishing}
            className="lg:w-full"
          >
            {readBack ? "Edit position" : published ? "Publish changes" : "Publish profile"}
          </BriefButton>
          <BriefButton
            variant="outline"
            onClick={onSaveDraft}
            disabled={readBack}
            className="lg:mt-2 lg:w-full"
          >
            Save draft
          </BriefButton>
        </div>
      </div>
    </aside>
  );
}

/**
 * Where the brief says whether it is stored. A refused save stays on screen until one lands — it is
 * the only place a consultant learns their typing is not yet on the server.
 */
function SaveStatusLine({ status, onRetry }: { status: SaveStatus; onRetry: () => void }) {
  return (
    <span aria-live="polite" className="text-meta text-u-text3 lg:mb-1 lg:text-center">
      {status === "error" ? (
        <button
          type="button"
          onClick={onRetry}
          className="font-medium text-u-offlimits underline-offset-2 hover:underline"
        >
          Not saved — Retry
        </button>
      ) : status === "saving" ? (
        "Saving…"
      ) : status === "saved" ? (
        "Saved"
      ) : (
        "\u00a0"
      )}
    </span>
  );
}
