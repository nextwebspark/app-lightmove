import { UncavaRailNav } from "../../../components/layout/UncavaRailNav";
import type { SaveStatus } from "../../../lib/useAutosave";
import type { Position } from "../api/types";
import { fieldCountOf, type Receipts } from "../lib/documentFill";
import { POSITION_STEPS, STEP_PARAM, type StepKey } from "../lib/steps";
import { BriefButton } from "./BriefFields";

/**
 * The rail beside the brief: the five steps as the report draws its chapters — one line each — and
 * at the foot the two acts, declaring the brief ready or putting the work down half-done.
 *
 * <p>The column is given the viewport's height so the buttons sit at its foot whatever the step
 * beside them measures; an auto-height box would leave Publish at the page bottom on a long step.
 * Below `lg` the steps are a strip above the step and the buttons a row under it.
 *
 * <p>A published brief that nobody has reopened offers <b>Edit position</b> instead, and no draft to
 * save: the brief on screen is the published one, so saving it again would record nothing.
 */
export function BriefRail({
  position,
  activeKey,
  saveStatus,
  publishing,
  readBack,
  receipts,
  onPublish,
  onEditPosition,
  onSaveDraft,
}: {
  position: Position;
  activeKey: StepKey;
  saveStatus: SaveStatus;
  publishing: boolean;
  /** Published and not reopened: the brief reads back and the rail offers the way in. */
  readBack: boolean;
  /** This session's document-reading receipts — the source of the "N filled" badge, never a persisted
   *  count: a brief somebody finished a month ago must not nag about fields nobody has re-read since. */
  receipts: Receipts;
  onPublish: () => void;
  onEditPosition: () => void;
  onSaveDraft: () => void;
}) {
  const published = Boolean(position.publication.publishedAt);
  const stepLinks = POSITION_STEPS.map((step) => {
    const count = fieldCountOf(receipts[step.key]);
    return { key: step.key, label: step.name, icon: step.icon, badge: count > 0 ? `${count} filled` : undefined };
  });

  return (
    <aside className="flex-none border-b border-u-border lg:w-[258px] lg:border-b-0 lg:border-r">
      <div className="flex flex-col gap-3 px-4 py-3.5 lg:sticky lg:top-0 lg:h-[calc(100dvh-62px)] lg:gap-0 lg:overflow-y-auto lg:px-[22px] lg:py-[30px]">
        <UncavaRailNav label="Brief steps" param={STEP_PARAM} items={stepLinks} activeKey={activeKey} />

        <div className="flex flex-wrap items-center gap-2 lg:mt-auto lg:flex-col lg:items-stretch lg:pt-6">
          <span aria-live="polite" className="text-meta text-u-text3 lg:mb-1 lg:text-center">
            {saveStatus === "saving" ? "Saving…" : saveStatus === "saved" ? "Saved" : " "}
          </span>
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
