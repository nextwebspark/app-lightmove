import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { FormError, LinesSkeleton } from "../../../../components/ui";
import { Chip } from "../../../../components/ui/Chip";
import { ToolbarSearch } from "../../../../components/ui/Toolbar";
import { cn } from "../../../../lib/cn";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import * as promptApi from "../api/promptApi";
import type { PromptLiveHealth, PromptOverview } from "../api/types";
import { OriginPill, PromptPill } from "../components/PromptPill";
import {
  changedLabel,
  healthPillLabel,
  healthPillTitle,
  isRetiringSoon,
  needsAttention,
  promptPath,
  MODEL_UNAVAILABLE_HINT,
  retirementLabel,
  versionLabel,
} from "../lib/promptLabels";

type PromptFilter = "all" | "edited" | "attention";

const FILTERS: readonly { key: PromptFilter; label: string }[] = [
  { key: "all", label: "All" },
  { key: "edited", label: "Edited" },
  { key: "attention", label: "Needs attention" },
];

const ROW_GRID = "grid min-w-[900px] grid-cols-[minmax(240px,2.2fr)_minmax(120px,.9fr)_minmax(150px,1.1fr)_minmax(180px,1.3fr)_84px] items-center gap-3.5 px-4";

/** Platform → Prompts: one row per AI prompt, with the version that serves. */
export default function PromptListPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<PromptFilter>("all");

  const { data: prompts, isPending, isError, error } = useQuery({
    queryKey: promptApi.PROMPTS_KEY,
    queryFn: ({ signal }) => promptApi.listPrompts(signal),
  });

  const rows = useMemo(() => matching(prompts ?? [], query, filter), [prompts, query, filter]);

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;

  return (
    <div>
      <PageHeader
        title="Prompts"
        subtitle="The instructions behind each AI feature. Every save is a new version; nothing serves it until it is put live, and an earlier version can be put back at any time."
      />

      <div className="mb-3.5 flex flex-wrap items-center gap-2">
        <ToolbarSearch value={query} onChange={setQuery} label="Search prompts" placeholder="Search prompts..." className="w-[260px] max-w-full" />
        <div role="radiogroup" aria-label="Show" className="flex flex-wrap gap-1.5">
          {FILTERS.map(({ key, label }) => (
            <Chip key={key} role="radio" aria-checked={filter === key} selected={filter === key} onClick={() => setFilter(key)}>
              {label}
            </Chip>
          ))}
        </div>
      </div>

      {isPending ? (
        <LinesSkeleton lines={8} />
      ) : isError ? (
        <FormError message={messageFor(error)} />
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
            <div role="table" aria-label="Prompts" className="overflow-x-auto">
              <div role="row" className={cn(ROW_GRID, "border-b border-u-border-strong bg-u-sunken py-2.5")}>
                {["Prompt", "Live version", "Model", "Last change"].map((heading) => (
                  <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                    {heading}
                  </span>
                ))}
                <span role="columnheader" />
              </div>
              {rows.map((prompt) => (
                <PromptRow key={prompt.id} prompt={prompt} onOpen={() => navigate(promptPath(prompt.id))} />
              ))}
              {rows.length === 0 && (
                <div className="px-4 py-10 text-center font-mono text-body text-u-text3">
                  No prompts match. Clear the search or pick another filter.
                </div>
              )}
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-4 text-xs text-u-text3">
            <span>
              {rows.length} of {prompts.length} prompts
            </span>
            <span>Shipped: the text that came with the release. Edited: a version saved here.</span>
          </div>
        </>
      )}
    </div>
  );
}

function PromptRow({ prompt, onOpen }: { prompt: PromptOverview; onOpen: () => void }) {
  return (
    <div
      role="row"
      // A mouse convenience only: the Open link is the row's keyboard-reachable way in.
      onClick={onOpen}
      className={cn(ROW_GRID, "cursor-pointer border-b border-u-border py-[11px] last:border-b-0 hover:bg-u-sunken")}
    >
      <span role="cell" className="block min-w-0">
        <span className="flex flex-wrap items-center gap-2">
          <span className="text-body font-semibold text-u-text">{prompt.name}</span>
          {prompt.health && <HealthPills health={prompt.health} />}
          {prompt.shippedChanged && (
            <PromptPill
              tone="warning"
              title="A release shipped a new version of this prompt after the live edit was made. The edit still serves."
            >
              Shipped prompt changed
            </PromptPill>
          )}
          {prompt.modelUnavailable && (
            <PromptPill tone="danger" title={MODEL_UNAVAILABLE_HINT}>
              Model unavailable
            </PromptPill>
          )}
          {!prompt.modelUnavailable && prompt.modelRetiresInDays !== null && isRetiringSoon(prompt.modelRetiresInDays) && (
            <PromptPill tone="danger">{retirementLabel(prompt.modelRetiresInDays)}</PromptPill>
          )}
        </span>
        <span className="mt-0.5 block truncate text-xs text-u-text2">{prompt.description}</span>
        <span className="mt-0.5 block font-u-num text-xs text-u-text3">{prompt.id}</span>
      </span>
      <span role="cell" className="flex items-center gap-2">
        <span className="font-u-num text-body text-u-text">{versionLabel(prompt.liveVersion)}</span>
        <OriginPill origin={prompt.origin} />
      </span>
      <span role="cell" className={cn("text-body", prompt.modelName ? "text-u-text2" : "text-u-text3")}>
        {prompt.modelName ?? "Default model"}
      </span>
      <span role="cell" className="text-body text-u-text2">
        {changedLabel(prompt.lastChangedAt, prompt.lastChangedBy)}
      </span>
      <span role="cell" className="flex justify-end">
        <Link
          to={promptPath(prompt.id)}
          // The row would navigate again on the bubbled click, pushing a second history entry.
          onClick={(event) => event.stopPropagation()}
          aria-label={`Open ${prompt.name}`}
          className="flex items-center gap-1.5 rounded-md border border-u-border-strong px-2.5 py-1 text-xs font-medium text-u-text2 hover:text-u-text"
        >
          Open
          <Icon d={ICONS.arrowRight} size={13} />
        </Link>
      </span>
    </div>
  );
}

/** The live version is clearly doing worse than the one it replaced: red for failures, amber for answers kept less. */
function HealthPills({ health }: { health: PromptLiveHealth }) {
  return health.warnings.map((warning) => (
    <PromptPill
      key={warning}
      tone={warning === "MORE_FAILURES" ? "danger" : "warning"}
      title={healthPillTitle(warning, health)}
    >
      {healthPillLabel(warning, health)}
    </PromptPill>
  ));
}

function matching(prompts: PromptOverview[], query: string, filter: PromptFilter): PromptOverview[] {
  const sought = query.trim().toLowerCase();
  return prompts.filter((prompt) => {
    if (filter === "edited" && prompt.origin !== "EDITED") return false;
    if (filter === "attention" && !needsAttention(prompt)) return false;
    return sought === "" || prompt.name.toLowerCase().includes(sought) || prompt.id.toLowerCase().includes(sought);
  });
}
