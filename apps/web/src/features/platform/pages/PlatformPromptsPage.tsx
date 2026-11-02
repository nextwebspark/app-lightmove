import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { PageHeader } from "../../../components/layout/PageHeader";
import { CardsSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { listPrompts, PLATFORM_PROMPTS_KEY } from "../api/platformConfigApi";
import type { PlatformPromptSummary } from "../api/types";
import { ConfigRefreshButton } from "../components/ConfigRefreshButton";
import { PromptStatusPill } from "../components/PromptStatusPill";

/** Settings → Platform → Prompts, a super admin's: every AI feature's instructions, each opened to edit. */
export function PlatformPromptsPage() {
  const prompts = useQuery({ queryKey: PLATFORM_PROMPTS_KEY, queryFn: ({ signal }) => listPrompts(signal) });

  return (
    <>
      <PageHeader
        title="Prompts"
        subtitle="The instructions each AI feature sends the model. A saved prompt is used from the next AI call, on every workspace. The shipped prompt is always one click away."
        action={<ConfigRefreshButton page="prompts" config={prompts.data?.config} />}
      />

      {prompts.isError ? (
        <p className="text-body text-u-text3">{messageFor(prompts.error)}</p>
      ) : prompts.isPending ? (
        <CardsSkeleton />
      ) : (
        <div className="rounded-u-card border border-u-border bg-u-surface px-4">
          <div className="type-micro-label flex items-center gap-3 border-b border-u-border py-2.5 font-mono text-u-text3">
            <span className="min-w-0 flex-1">Prompt</span>
            <span className="w-[120px]">Status</span>
            <span className="hidden w-[190px] text-end sm:block">Last changed</span>
          </div>
          <ul className="divide-y divide-u-border">
            {prompts.data.prompts.map((prompt) => (
              <PromptRow key={prompt.id} prompt={prompt} />
            ))}
          </ul>
        </div>
      )}

      <p className="mt-3 max-w-[680px] font-mono text-note text-u-text3">
        Contacts, pay and custom fields are taken out before anything reaches the model, whatever a prompt says.
        Answer formats are fixed in code and cannot be changed here.
      </p>
    </>
  );
}

function PromptRow({ prompt }: { prompt: PlatformPromptSummary }) {
  const changed = prompt.overridden
    ? [prompt.updatedByName, formatInstantDate(prompt.updatedAt)].filter(Boolean).join(" · ")
    : "";
  return (
    <li>
      <Link
        to={`/settings/platform-prompts/${encodeURIComponent(prompt.id)}`}
        className="flex items-center gap-3 py-3 hover:bg-u-raised"
      >
        <span className="min-w-0 flex-1">
          <span className="block text-body font-medium text-u-text">{prompt.label}</span>
          <span className="mt-0.5 block font-mono text-meta text-u-text3">{prompt.id}</span>
        </span>
        <span className="w-[120px]">
          <PromptStatusPill activeVersion={prompt.activeVersion} />
        </span>
        <span className="hidden w-[190px] text-end font-mono text-note text-u-text3 sm:block">{changed || "—"}</span>
      </Link>
    </li>
  );
}
