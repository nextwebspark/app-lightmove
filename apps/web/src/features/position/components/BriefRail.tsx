import { UncavaRailNav } from "../../../components/layout/UncavaRailNav";
import type { SaveStatus } from "../../../lib/useAutosave";
import type { Position } from "../api/types";
import { POSITION_STEPS, STEP_PARAM, type StepKey } from "../lib/steps";
import { BriefButton } from "./BriefFields";

const STEP_LINKS = POSITION_STEPS.map((step) => ({ key: step.key, label: step.name, icon: step.icon }));

/**
 * The rail beside the brief: the five steps as the report draws its chapters — one line each — and
 * at the foot the two acts, declaring the brief ready or putting the work down half-done.
 *
 * <p>The column is given the viewport's height so the buttons sit at its foot whatever the step
 * beside them measures; an auto-height box would leave Publish at the page bottom on a long step.
 * Below `lg` the steps are a strip above the step and the buttons a row under it.
 */
export function BriefRail({
  position,
  activeKey,
  saveStatus,
  publishing,
  onPublish,
  onSaveDraft,
}: {
  position: Position;
  activeKey: StepKey;
  saveStatus: SaveStatus;
  publishing: boolean;
  onPublish: () => void;
  onSaveDraft: () => void;
}) {
  const published = Boolean(position.publication.publishedAt);

  return (
    <aside className="flex-none border-b border-u-border lg:w-[258px] lg:border-b-0 lg:border-r">
      <div className="flex flex-col gap-3 px-4 py-3.5 lg:sticky lg:top-0 lg:h-[calc(100dvh-62px)] lg:gap-0 lg:overflow-y-auto lg:px-[22px] lg:py-[30px]">
        <UncavaRailNav label="Brief steps" param={STEP_PARAM} items={STEP_LINKS} activeKey={activeKey} />

        <div className="flex flex-wrap items-center gap-2 lg:mt-auto lg:flex-col lg:items-stretch lg:pt-6">
          <span aria-live="polite" className="text-[11px] text-u-text3 lg:mb-1 lg:text-center">
            {saveStatus === "saving" ? "Saving…" : saveStatus === "saved" ? "Saved" : " "}
          </span>
          <BriefButton onClick={onPublish} loading={publishing} className="lg:w-full">
            {published ? "Publish changes" : "Publish profile"}
          </BriefButton>
          <BriefButton variant="outline" onClick={onSaveDraft} className="lg:mt-2 lg:w-full">
            Save draft
          </BriefButton>
        </div>
      </div>
    </aside>
  );
}
