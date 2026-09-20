import { Link } from "react-router-dom";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { SaveStatus } from "../../../lib/useAutosave";
import type { Position } from "../api/types";
import { POSITION_STEPS, STEP_PARAM, type StepKey } from "../lib/steps";
import { BriefButton } from "./BriefFields";

/**
 * The rail beside the brief: the five steps, each reading back one line of what it holds, and at the
 * bottom the two acts — declaring the brief ready, or putting the work down half-done.
 *
 * <p>Built like the report's `ReportNav`: a column at `lg`, a strip above the step below it, because
 * the rail is the only way between steps and cannot be hidden on a phone. Steps are links, not
 * buttons: the step lives in the URL, so a colleague can be sent straight to Compensation and the
 * back button walks the steps.
 *
 * <p>The column is given the viewport's height so the buttons can sit at its foot whatever the step
 * beside them measures; an auto-height box would leave Publish at the page bottom on a long step.
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
      <div className="flex flex-col gap-3 px-4 py-3.5 lg:sticky lg:top-0 lg:h-[calc(100dvh-62px)] lg:gap-0 lg:overflow-y-auto lg:px-[18px] lg:py-[26px]">
        <nav aria-label="Brief steps" className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
          {POSITION_STEPS.map((step) => {
            const isActive = step.key === activeKey;
            return (
              <Link
                key={step.key}
                to={{ search: `?${STEP_PARAM}=${step.key}` }}
                aria-current={isActive ? "page" : undefined}
                className={cn(
                  "flex min-w-0 flex-none items-start gap-2.5 rounded-[8px] px-3 py-2.5 transition",
                  isActive
                    ? "bg-u-accent-tint text-u-accent shadow-[inset_2px_0_0_var(--color-u-accent)]"
                    : "text-u-text2 hover:bg-u-raised hover:text-u-text",
                )}
              >
                <Icon
                  d={step.icon}
                  size={15}
                  className={cn("mt-0.5 flex-none", isActive ? "text-u-accent" : "text-u-text3")}
                />
                <span className="min-w-0">
                  <span className="block truncate text-[13px] font-semibold">{step.name}</span>
                  <span className={cn("block truncate text-[11px]", isActive ? "text-u-accent/80" : "text-u-text3")}>
                    {step.summary(position)}
                  </span>
                </span>
              </Link>
            );
          })}
        </nav>

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
