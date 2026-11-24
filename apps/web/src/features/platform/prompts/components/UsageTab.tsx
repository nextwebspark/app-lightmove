import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { FormError, LinesSkeleton } from "../../../../components/ui";
import { Chip } from "../../../../components/ui/Chip";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { formatNumber } from "../../../../lib/format";
import * as promptApi from "../api/promptApi";
import type { PromptModelUsage, PromptUsage, PromptVersionUsage } from "../api/types";
import {
  compactNumber,
  DEFAULT_USAGE_SPAN,
  failuresOf,
  fractionPercentLabel,
  USAGE_SPANS,
  versionLabel,
  type UsageSpan,
} from "../lib/promptLabels";
import { PromptPill } from "./PromptPill";

const ROW_GRID =
  "grid min-w-[820px] grid-cols-[58px_minmax(96px,1fr)_52px_minmax(140px,1.3fr)_82px_62px_minmax(120px,1fr)_minmax(104px,.9fr)] items-center gap-3 px-4";

const HEADINGS: readonly { label: string; title?: string }[] = [
  { label: "Version" },
  { label: "Model" },
  { label: "Calls" },
  { label: "Failed" },
  { label: "Asked again", title: "An answer off its format, asked for once more. Each is also a call." },
  { label: "Stopped", title: "Text that read like an instruction to the model, stopped before it was sent. Not a call." },
  { label: "Tokens" },
  { label: "Kept" },
];

/** Usage: what each version did where it served, by model. Counts only; a Try it and an eval run are left out. */
export function UsageTab({ promptId }: { promptId: string }) {
  const [days, setDays] = useState<UsageSpan>(DEFAULT_USAGE_SPAN);
  const { data: usage, isPending, isError, error } = useQuery({
    queryKey: promptApi.PROMPT_USAGE_KEY(promptId, days),
    queryFn: ({ signal }) => promptApi.getPromptUsage(promptId, days, signal),
  });

  return (
    <div>
      <div role="radiogroup" aria-label="Span" className="mb-3.5 flex flex-wrap gap-1.5">
        {USAGE_SPANS.map((span) => (
          <Chip key={span} role="radio" aria-checked={days === span} selected={days === span} onClick={() => setDays(span)}>
            Last {span} days
          </Chip>
        ))}
      </div>

      {isPending ? (
        <LinesSkeleton lines={4} />
      ) : isError ? (
        <FormError message={messageFor(error)} />
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
            <div role="table" aria-label="Usage by version and model" className="overflow-x-auto">
              <div role="row" className={cn(ROW_GRID, "border-b border-u-border-strong bg-u-sunken py-2.5")}>
                {HEADINGS.map(({ label, title }) => (
                  <span key={label} role="columnheader" title={title} className="type-micro-label font-mono text-u-text3">
                    {label}
                  </span>
                ))}
              </div>
              {usage.versions.map((version) => (
                <VersionRows key={version.version} version={version} decisionsCounted={usage.decisionsCounted} />
              ))}
              {usage.versions.length === 0 && (
                <div className="px-4 py-10 text-center font-mono text-body text-u-text3">
                  No calls in the last {usage.days} days.
                </div>
              )}
            </div>
          </div>
          <p className="mx-0.5 mt-3 text-xs text-u-text3">
            Failed: a call cut off at the output limit, stopped by the safety filter, or answered in a way the feature
            could not read. {keptNote(usage)} Counts only; Try it and eval runs are left out.
          </p>
        </>
      )}
    </div>
  );
}

function keptNote(usage: PromptUsage): string {
  return usage.decisionsCounted
    ? "Kept: of the answers a person decided on, the share they left as written."
    : "Kept is not counted for this prompt: no screen asks a person to keep or change its answer.";
}

/**
 * One row per model the version answered on. What people kept is the version's, not a model's, so it is drawn once,
 * on the version's first row; a version decided on in the span with no calls in it still gets that row.
 */
function VersionRows({ version, decisionsCounted }: { version: PromptVersionUsage; decisionsCounted: boolean }) {
  const models: (PromptModelUsage | null)[] = version.models.length > 0 ? version.models : [null];
  return (
    <>
      {models.map((model, index) => (
        <div
          key={model ? `${model.provider}:${model.modelId}` : "none"}
          role="row"
          className={cn(ROW_GRID, "border-b border-u-border py-[11px] last:border-b-0")}
        >
          <span role="cell" className="flex items-center gap-2">
            <span className="font-u-num text-body text-u-text">{versionLabel(version.version)}</span>
            {version.live && <PromptPill tone="live">Live</PromptPill>}
          </span>
          <span role="cell" className={cn("text-body", model ? "text-u-text2" : "text-u-text3")}>
            {model ? model.modelName : "No calls"}
          </span>
          <span role="cell" className="font-u-num text-body text-u-text">
            {formatNumber(model?.calls ?? 0)}
          </span>
          <span role="cell" className="block min-w-0">
            {model && <FailedCell model={model} />}
          </span>
          <span role="cell" className="font-u-num text-body text-u-text">
            {model ? formatNumber(model.schemaReasks) : ""}
          </span>
          <span role="cell" className="font-u-num text-body text-u-text">
            {model ? formatNumber(model.guardRefusals) : ""}
          </span>
          <span role="cell" className="text-body text-u-text2">
            {model ? `${compactNumber(model.inputTokens)} in · ${compactNumber(model.outputTokens)} out` : ""}
          </span>
          <span role="cell" className="block min-w-0">
            {index === 0 && <KeptCell version={version} decisionsCounted={decisionsCounted} />}
          </span>
        </div>
      ))}
    </>
  );
}

function FailedCell({ model }: { model: PromptModelUsage }) {
  const failed = failuresOf(model);
  return (
    <>
      <span className="block font-u-num text-body text-u-text">
        {formatNumber(failed)}
        {model.calls > 0 && ` · ${fractionPercentLabel(failed / model.calls)}`}
      </span>
      <span className="block text-xs text-u-text3">
        {formatNumber(model.finishMaxTokens)} cut off · {formatNumber(model.finishSafety)} safety ·{" "}
        {formatNumber(model.schemaFailures)} unreadable
      </span>
    </>
  );
}

function KeptCell({ version, decisionsCounted }: { version: PromptVersionUsage; decisionsCounted: boolean }) {
  if (!decisionsCounted) return <span className="block text-body text-u-text3">Not counted</span>;
  const decided = version.accepted + version.changed;
  return (
    <>
      <span className={cn("block font-u-num text-body", decided > 0 ? "text-u-text" : "text-u-text3")}>
        {decided > 0 ? fractionPercentLabel(version.accepted / decided) : "None decided"}
      </span>
      <span className="block text-xs text-u-text3">
        {formatNumber(version.accepted)} kept · {formatNumber(version.changed)} changed
      </span>
    </>
  );
}
