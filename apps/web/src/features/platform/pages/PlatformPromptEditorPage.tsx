import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DrawerSkeleton, TextArea, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import {
  getPrompt,
  PLATFORM_PROMPT_KEY,
  PLATFORM_PROMPTS_KEY,
  restorePromptVersion,
  revertPrompt,
  savePrompt,
} from "../api/platformConfigApi";
import type { PlatformPromptDetail } from "../api/types";
import { ConfigRefreshButton } from "../components/ConfigRefreshButton";
import { PromptStatusPill } from "../components/PromptStatusPill";
import { placeholderChange } from "../lib/placeholders";

/**
 * One prompt, a super admin's to change: a plain monospace editor, Save, Revert to shipped and the saved versions,
 * each one click from being in use again. A save that drops or adds a placeholder is refused before it is sent,
 * and again by the server.
 */
export function PlatformPromptEditorPage() {
  const { promptId = "" } = useParams();
  const prompt = useQuery({
    queryKey: PLATFORM_PROMPT_KEY(promptId),
    queryFn: ({ signal }) => getPrompt(promptId, signal),
  });

  return (
    <>
      <Link
        to="/settings/platform-prompts"
        className="mb-3 inline-flex items-center gap-1.5 text-note font-medium text-u-text3 hover:text-u-text"
      >
        <Icon d={ICONS.arrowLeft} size={14} />
        Prompts
      </Link>
      {prompt.isError ? (
        <p className="text-body text-u-text3">{messageFor(prompt.error)}</p>
      ) : prompt.isPending ? (
        <DrawerSkeleton />
      ) : (
        <PromptEditor key={`${prompt.data.id}:${prompt.data.activeVersion ?? "shipped"}`} detail={prompt.data} />
      )}
    </>
  );
}

function PromptEditor({ detail }: { detail: PlatformPromptDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const inUse = detail.activeBody ?? detail.shippedBody;
  const [draft, setDraft] = useState(inUse);
  const [problem, setProblem] = useState<string | null>(null);

  const settle = (next: PlatformPromptDetail, message: string) => {
    queryClient.setQueryData(PLATFORM_PROMPT_KEY(detail.id), next);
    void queryClient.invalidateQueries({ queryKey: PLATFORM_PROMPTS_KEY, exact: true });
    toast(message);
  };

  const saving = useMutation({
    mutationFn: () => savePrompt(detail.id, draft, detail.activeVersion),
    onSuccess: (next) => settle(next, `Version ${next.activeVersion} is in use from the next AI call`),
    onError: (error) => {
      if (codeOf(error) === "PLATFORM_PROMPT_VARIABLES_CHANGED" && error instanceof ApiRequestError) {
        setProblem(describeChange(error.problem.missing ?? [], error.problem.added ?? []));
        return;
      }
      const named = error instanceof ApiRequestError ? error.fieldErrors.body : undefined;
      if (named) setProblem(named);
      else toast.error(messageFor(error));
    },
  });

  const restoring = useMutation({
    mutationFn: (version: number) => restorePromptVersion(detail.id, version),
    onSuccess: (next, version) => settle(next, `Version ${version} restored as version ${next.activeVersion}`),
    onError: (error) => toast.error(messageFor(error)),
  });

  const reverting = useMutation({
    mutationFn: () => revertPrompt(detail.id),
    onSuccess: (next) => settle(next, "Back to the shipped prompt"),
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleSave = () => {
    if (!draft.trim()) {
      setProblem("Write the prompt");
      return;
    }
    const change = placeholderChange(detail.variables, draft);
    if (change.missing.length > 0 || change.added.length > 0) {
      setProblem(describeChange(change.missing, change.added));
      return;
    }
    saving.mutate();
  };

  const busy = saving.isPending || restoring.isPending || reverting.isPending;

  return (
    <>
      <div className="mb-3.5 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="type-title">{detail.label}</h1>
            <PromptStatusPill activeVersion={detail.activeVersion} />
          </div>
          <div className="mt-1 font-mono text-meta text-u-text3">{detail.resource}</div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ConfigRefreshButton page="prompts" config={detail.config} />
          {detail.activeVersion !== null && (
            <Button variant="secondary" size="sm" disabled={busy} loading={reverting.isPending} onClick={() => reverting.mutate()}>
              Revert to shipped
            </Button>
          )}
          <Button size="sm" disabled={busy || draft === inUse} loading={saving.isPending} onClick={handleSave}>
            Save
          </Button>
        </div>
      </div>

      <TextArea
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
          setProblem(null);
        }}
        aria-label="Prompt text"
        spellCheck={false}
        invalid={!!problem}
        maxLength={100_000}
        className="min-h-[440px] text-note"
      />
      <span aria-live="polite" className="mt-1.5 block font-mono text-meta">
        {problem ? (
          <span className="text-u-offlimits">{problem}</span>
        ) : (
          <span className="text-u-text3">{placeholderHint(detail.variables)}</span>
        )}
      </span>

      <section aria-label="Versions" className="mt-4 rounded-u-card border border-u-border bg-u-surface px-4">
        <h2 className="type-micro-label py-2.5 font-mono text-u-text3">Versions</h2>
        <ul className="divide-y divide-u-border border-t border-u-border">
          <VersionRow label="Shipped" meta="Comes with this release" active={detail.activeVersion === null} />
          {detail.versions.map((version) => (
            <VersionRow
              key={version.version}
              label={`Version ${version.version}`}
              meta={[version.createdByName, formatInstantDate(version.createdAt)].filter(Boolean).join(" · ")}
              active={version.active}
              onRestore={busy ? undefined : () => restoring.mutate(version.version)}
            />
          ))}
        </ul>
      </section>
    </>
  );
}

function VersionRow({
  label,
  meta,
  active,
  onRestore,
}: {
  label: string;
  meta: string;
  active: boolean;
  onRestore?: () => void;
}) {
  return (
    <li className="flex items-center gap-2 py-2.5">
      <span className="min-w-0 flex-1">
        <span className="block text-body font-medium text-u-text">{label}</span>
        <span className="mt-0.5 block font-mono text-meta text-u-text3">{meta}</span>
      </span>
      {active ? (
        <span className="font-mono text-meta font-medium text-u-accent">In use</span>
      ) : (
        label !== "Shipped" && (
          <Button variant="ghost" size="xs" disabled={!onRestore} onClick={onRestore}>
            Restore
          </Button>
        )
      )}
    </li>
  );
}

function placeholderHint(variables: string[]): string {
  if (variables.length === 0) return "This prompt takes no placeholders, so it may not add any {braces} of its own.";
  return `Keep these placeholders exactly as written: ${variables.map((name) => `{${name}}`).join(" ")}`;
}

function describeChange(missing: string[], added: string[]): string {
  const braces = (names: string[]) => names.map((name) => `{${name}}`).join(", ");
  return [missing.length > 0 && `Put back ${braces(missing)}`, added.length > 0 && `Take out ${braces(added)}`]
    .filter(Boolean)
    .join(". ");
}

