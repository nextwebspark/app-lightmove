import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, CardsSkeleton, FormError, LinesSkeleton, useToast } from "../../../../components/ui";
import { TabList, type TabOption } from "../../../../components/ui/TabList";
import { tabPanelProps } from "../../../../components/ui/tabPanelProps";
import { cn } from "../../../../lib/cn";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { useAuth } from "../../../auth/AuthProvider";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { usePlatformPageCrumb } from "../../platformCrumb";
import * as promptApi from "../api/promptApi";
import type { PromptDetail, PromptVersionSummary } from "../api/types";
import { PromptEditorTab } from "../components/PromptEditorTab";
import { OriginPill, PromptPill } from "../components/PromptPill";
import { PutLiveDialog } from "../components/PutLiveDialog";
import { TrialTab } from "../components/TrialTab";
import { UsageTab } from "../components/UsageTab";
import { VersionCompareTab } from "../components/VersionCompareTab";
import {
  changedLabel,
  healthNote,
  healthTone,
  PROMPTS_PATH,
  PUT_LIVE_PARAM,
  VERSION_PARAM,
  versionLabel,
  versionModelLine,
} from "../lib/promptLabels";

type PromptTab = "editor" | "compare" | "try" | "usage";

const TAB_ID = "prompt";

const HEALTH_NOTE_TONE = {
  danger: { note: "bg-u-offlimits-tint", icon: "text-u-offlimits" },
  warning: { note: "bg-u-accent-tint", icon: "text-u-accent" },
} as const;

/** Platform → Prompts → one prompt: its versions, the editor, a comparison of any two, Try it, and its usage. */
export default function PromptPage() {
  const { promptId = "" } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const linkedVersion = Number(params.get(VERSION_PARAM)) || null;
  const [openedVersion, setOpenedVersion] = useState<number | null>(linkedVersion);
  const [tab, setTab] = useState<PromptTab>("editor");
  const [chosenToMove, setChosenToMove] = useState<number | null>(null);

  const { data: detail, isPending, isFetching, error } = useQuery({
    queryKey: promptApi.PROMPT_KEY(promptId),
    queryFn: ({ signal }) => promptApi.getPrompt(promptId, signal),
  });
  const { data: models } = useQuery({
    queryKey: promptApi.AI_MODELS_KEY,
    queryFn: ({ signal }) => promptApi.listAiModels(signal),
  });

  usePlatformPageCrumb(detail?.prompt.name ?? null);

  const canPromote = user?.platformActions.includes("PROMPT_PROMOTE") ?? false;
  const isPutLiveLinked = params.has(PUT_LIVE_PARAM);
  // A run's page links here with its candidate and Put live already asked for. The run has just changed what the
  // evals say about that version, so the dialog waits for the prompt to be read again: on the cached copy it would
  // ask for a reason the run has made unnecessary. Once open it is the page's own, so a later read cannot close it.
  useEffect(() => {
    if (!isPutLiveLinked || !detail || isFetching) return;
    const isOffered =
      canPromote &&
      openedVersion === linkedVersion &&
      linkedVersion !== detail.prompt.liveVersion &&
      detail.versions.some((version) => version.version === linkedVersion);
    if (isOffered) setChosenToMove(linkedVersion);
    setParams({}, { replace: true });
  }, [isPutLiveLinked, detail, isFetching, canPromote, openedVersion, linkedVersion, setParams]);

  const openNumber = openedVersion ?? detail?.prompt.liveVersion ?? null;
  const open = detail?.versions.find((version) => version.version === openNumber) ?? null;

  const body = useQuery({
    queryKey: promptApi.PROMPT_BODY_KEY(promptId, openNumber ?? 0),
    queryFn: ({ signal }) => promptApi.getPromptBody(promptId, openNumber ?? 0, signal),
    enabled: openNumber !== null,
    staleTime: Infinity,
  });

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;
  if (isPending) return <CardsSkeleton cards={2} />;
  if (!detail || !open) return <FormError message={error ? messageFor(error) : "This prompt could not be opened."} />;

  const { prompt } = detail;
  const { health } = prompt;
  const openIsLive = open.version === prompt.liveVersion;
  const moving: PromptVersionSummary | null =
    detail.versions.find((version) => version.version === chosenToMove) ?? null;
  const closeMove = () => setChosenToMove(null);
  const tabs: TabOption<PromptTab>[] = [
    { value: "editor", label: "Editor" },
    { value: "compare", label: "Compare versions" },
    ...(detail.trial ? [{ value: "try" as const, label: "Try it" }] : []),
    { value: "usage", label: "Usage" },
  ];

  const keep = (updated: PromptDetail) => {
    queryClient.setQueryData(promptApi.PROMPT_KEY(promptId), updated);
    void queryClient.invalidateQueries({ queryKey: promptApi.PROMPTS_KEY, exact: true });
  };

  return (
    <div>
      <Link to={PROMPTS_PATH} className="mb-3 inline-flex items-center gap-1.5 text-xs font-medium text-u-text3 hover:text-u-text">
        <Icon d={ICONS.back} size={13} />
        Prompts
      </Link>

      <div className="mb-4 flex flex-wrap items-start gap-x-4 gap-y-2.5">
        <div className="min-w-0 flex-[1_1_320px]">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-title font-semibold">{prompt.name}</h1>
            <PromptPill tone="live">Live: {versionLabel(prompt.liveVersion)}</PromptPill>
          </div>
          <div className="mt-1 text-body text-u-text2">{prompt.description}</div>
          <div className="mt-0.5 font-u-num text-xs text-u-text3">{prompt.id}</div>
        </div>
        <div className="flex flex-none flex-wrap items-center gap-2">
          {openIsLive && <span className="text-xs text-u-text3">Version {open.version} is live</span>}
          {!openIsLive && !canPromote && (
            <span title="Putting a version live needs its own permission" className="text-xs text-u-text3">
              You can save versions, not put them live
            </span>
          )}
          {!openIsLive && canPromote && open.version < prompt.liveVersion && (
            <Button type="button" size="sm" variant="secondary" onClick={() => setChosenToMove(open.version)}>
              Roll back to version {open.version}
            </Button>
          )}
          {!openIsLive && canPromote && open.version > prompt.liveVersion && (
            <Button type="button" size="sm" onClick={() => setChosenToMove(open.version)}>
              Put version {open.version} live
            </Button>
          )}
        </div>
      </div>

      {prompt.modelUnavailable && (
        <div role="note" className="mb-4 flex gap-2.5 rounded-lg border border-u-border bg-u-offlimits-tint px-3.5 py-[11px] text-xs text-u-text">
          <Icon d={ICONS.info} size={15} className="mt-0.5 flex-none text-u-offlimits" />
          <span>
            Version {prompt.liveVersion} names {prompt.modelName ?? "a model"}, which is retired, no longer offered or
            cannot answer this prompt. The default model answers it until a version on an available model is put live.
          </span>
        </div>
      )}

      {health?.warnings.map((warning) => (
        <div
          key={warning}
          role="note"
          className={cn(
            "mb-4 flex gap-2.5 rounded-lg border border-u-border px-3.5 py-[11px] text-xs text-u-text",
            HEALTH_NOTE_TONE[healthTone(warning)].note,
          )}
        >
          <Icon
            d={ICONS.info}
            size={15}
            className={cn("mt-0.5 flex-none", HEALTH_NOTE_TONE[healthTone(warning)].icon)}
          />
          <span>{healthNote(warning, health, prompt.liveVersion)}</span>
        </div>
      ))}

      {prompt.shippedChanged && detail.newestShippedVersion !== null && (
        <div role="note" className="mb-4 flex gap-2.5 rounded-lg border border-u-border bg-u-accent-tint px-3.5 py-[11px] text-xs text-u-text">
          <Icon d={ICONS.info} size={15} className="mt-0.5 flex-none text-u-accent" />
          <span>
            The shipped prompt changed since the live edit. Version {detail.newestShippedVersion} came with a release;
            version {prompt.liveVersion}, an edit, still serves. Compare the two and carry over what the release
            changed.
          </span>
        </div>
      )}

      <div className="flex flex-wrap items-start gap-5">
        <section
          aria-label="Version history"
          className="min-w-[220px] max-w-[300px] flex-[1_1_240px] overflow-hidden rounded-lg border border-u-border-strong bg-u-surface"
        >
          <div className="px-3 pb-2 pt-3 type-micro-label font-mono text-u-text3">Versions</div>
          {detail.versions.map((version) => (
            <button
              key={version.version}
              type="button"
              aria-current={version.version === open.version}
              onClick={() => setOpenedVersion(version.version)}
              className={cn(
                "block w-full border-l-2 border-t border-t-u-border px-3 py-2.5 text-left hover:bg-u-sunken",
                version.version === open.version ? "border-l-u-accent bg-u-sunken" : "border-l-transparent",
              )}
            >
              <span className="flex flex-wrap items-center gap-1.5">
                <span className="font-u-num text-body text-u-text">{versionLabel(version.version)}</span>
                {version.live && <PromptPill tone="live">Live</PromptPill>}
                <OriginPill origin={version.origin} />
              </span>
              <span className="mt-1 block text-xs text-u-text2">
                {version.changeNote ?? (version.origin === "BUNDLED" ? "Shipped with a release." : "")}
              </span>
              <span className="mt-0.5 block text-xs text-u-text3">
                {changedLabel(version.createdAt, version.origin === "BUNDLED" ? null : version.author)}
              </span>
              <span className="block text-xs text-u-text3">{versionModelLine(version)}</span>
            </button>
          ))}
        </section>

        <div className="min-w-0 flex-[3_1_520px]">
          <TabList label="Prompt" idPrefix={TAB_ID} tabs={tabs} value={tab} onChange={setTab} className="mb-3.5" />
          <div {...tabPanelProps(TAB_ID, tab)}>
            {tab === "editor" &&
              (body.isError ? (
                <FormError message={messageFor(body.error)} />
              ) : !body.data || !models ? (
                <LinesSkeleton lines={10} />
              ) : (
                <PromptEditorTab
                  key={open.version}
                  detail={detail}
                  models={models}
                  version={open}
                  body={body.data.body}
                  onSaved={(updated) => {
                    keep(updated);
                    setOpenedVersion(updated.versions[0].version);
                  }}
                />
              ))}
            {tab === "compare" && <VersionCompareTab key={open.version} detail={detail} openVersion={open.version} />}
            {tab === "try" && <TrialTab detail={detail} version={open.version} />}
            {tab === "usage" && <UsageTab promptId={prompt.id} />}
          </div>
        </div>
      </div>

      {moving && (
        <PutLiveDialog
          // What the evals say can change while the dialog is open (a run ending); it then starts over on that.
          key={`${moving.version}:${moving.gate?.state ?? ""}:${moving.gate?.runId ?? ""}`}
          detail={detail}
          target={moving}
          onClose={closeMove}
          onDone={(updated) => {
            keep(updated);
            closeMove();
            toast.success(`${prompt.name} now runs on version ${updated.prompt.liveVersion} in every workspace`);
          }}
        />
      )}
    </div>
  );
}
