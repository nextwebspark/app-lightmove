import { type ReactNode, useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { NotetakerRecording } from "../api/notetakerApi";
import { elapsedSince } from "../lib/copilotWords";
import { failureWordsOf } from "../lib/notetakerChip";
import { useNotetakerActions } from "../lib/useNotetakerActions";

/**
 * A call's ⋯, the one place its Notetaker shows on the row: a dot while it records or something waits for the
 * consultant, and inside, where the call has got to, its Notetaker & questions, what came of it once the notes are
 * filed — the changes, the transcript, Try again — then moving or cancelling it where it is the consultant's to change.
 * A call with none of these has no ⋯.
 */
export function CallMenu({
  projectId,
  candidateId,
  firstName,
  recording,
  options,
  onShowTranscript,
  onShowChanges,
  onOpenCopilot,
  onReschedule,
  onCancel,
}: {
  projectId: string;
  candidateId: string;
  firstName: string;
  /** The call's latest bot, where one was ever sent. */
  recording: NotetakerRecording | null;
  /** The call's panel, or the Interview plan where no Notetaker is offered; only on a call still to come. */
  options?: { label: string; icon: string; onSelect: () => void };
  onShowTranscript: (recordingId: string) => void;
  /** Opens what the transcript changes on the record: the review while changes wait, what was done after. */
  onShowChanges: (recordingId: string) => void;
  /** Opens the live copilot's window; called straight from the click, which the window needs. */
  onOpenCopilot: (recordingId: string) => void;
  onReschedule?: () => void;
  onCancel?: () => void;
}) {
  const actions = useNotetakerActions(projectId, candidateId, firstName);
  const bot = recording?.status === "CANCELLED" ? null : recording;
  const changes = bot?.status === "DONE" ? changesStepOf(bot) : null;
  const offersTranscript = bot?.status === "DONE" && bot.hasTranscript;
  const offersRetry = bot?.status === "FAILED" && bot.retryable;
  const offersCopilot =
    bot?.liveCopilot === true && (bot.status === "PROCESSING" || (bot.status === "RECORDING" && bot.endRequested));
  const hasCallItems = Boolean(options || changes?.action || offersTranscript || offersRetry || offersCopilot);
  const hasChangeItems = Boolean(onReschedule || onCancel);
  if (!bot && !hasCallItems && !hasChangeItems) return null;
  const mark = markOf(bot);

  return (
    <Popover
      label={mark ? `Call options · ${mark.words}` : "Call options"}
      title="Call options"
      align="right"
      placement="auto"
      width={240}
      hasPopup="menu"
      triggerClassName={cn(ICON_ACTION, "hover:bg-u-raised hover:text-u-text")}
      trigger={() => (
        <>
          <Icon d={ICONS.more} size={14} />
          {mark && (
            <span
              aria-hidden="true"
              className={cn(
                "absolute -right-1 -top-1 size-2 rounded-full ring-2 ring-u-surface",
                mark.tone === "recording" ? "animate-pulse bg-u-offlimits" : "bg-u-accent",
              )}
            />
          )}
        </>
      )}
    >
      {(close) => {
        const choose = (action: () => void) => () => {
          close();
          action();
        };
        return (
          <>
            {bot && <CallState recording={bot} />}
            {(hasCallItems || hasChangeItems) && (
              <div role="menu" aria-label="Call options" className="flex flex-col">
                {options && (
                  <MenuItem icon={options.icon} onSelect={choose(options.onSelect)}>
                    {options.label}
                  </MenuItem>
                )}
                {bot && changes?.action && (
                  <MenuItem icon={ICONS.sparkle} onSelect={choose(() => onShowChanges(bot.id))}>
                    {changes.action === "review"
                      ? `Review ${countOf(bot.pendingSuggestions, "change")}`
                      : changes.action === "view"
                        ? "Changes from the call"
                        : "Suggest changes"}
                  </MenuItem>
                )}
                {bot && offersTranscript && (
                  <MenuItem icon={ICONS.fileText} onSelect={choose(() => onShowTranscript(bot.id))}>
                    Transcript
                  </MenuItem>
                )}
                {bot && offersRetry && (
                  <MenuItem icon={ICONS.pencil} onSelect={choose(() => actions.retry.mutate(bot.id))}>
                    Try again
                  </MenuItem>
                )}
                {bot && offersCopilot && (
                  <MenuItem icon={ICONS.externalLink} onSelect={choose(() => onOpenCopilot(bot.id))}>
                    Open copilot
                  </MenuItem>
                )}
                {hasCallItems && hasChangeItems && <div role="separator" className="my-1 h-px bg-u-border" />}
                {onReschedule && (
                  <MenuItem icon={ICONS.reschedule} onSelect={choose(onReschedule)}>
                    Reschedule
                  </MenuItem>
                )}
                {onCancel && (
                  <MenuItem icon={ICONS.trash} tone="danger" onSelect={choose(onCancel)}>
                    Cancel call
                  </MenuItem>
                )}
              </div>
            )}
          </>
        );
      }}
    </Popover>
  );
}

/** What puts a dot on the ⋯: a call being recorded, or one waiting on the consultant. */
function markOf(recording: NotetakerRecording | null): { tone: "recording" | "waiting"; words: string } | null {
  if (recording?.status === "RECORDING" && !recording.endRequested) return { tone: "recording", words: "recording" };
  if (recording?.status === "DONE" && recording.pendingSuggestions > 0) {
    return { tone: "waiting", words: `${countOf(recording.pendingSuggestions, "change")} to review` };
  }
  if (recording?.status === "FAILED" && recording.retryable) return { tone: "waiting", words: "no notes" };
  return null;
}

/** Where the call has got to, in the words the menu opens on. */
function CallState({ recording }: { recording: NotetakerRecording }) {
  switch (recording.status) {
    case "SCHEDULED":
    case "JOINING":
      if (recording.status === "SCHEDULED" && recording.joinAt !== null) {
        return (
          <MenuHead icon={<Icon d={ICONS.mic} size={13} className="text-u-accent" />}>
            Notetaker joins at start
            <Dim parts={[recording.liveCopilot ? "Live copilot" : null]} />
          </MenuHead>
        );
      }
      return (
        <MenuHead icon={<Turning />}>
          Notetaker joining
          <Dim parts={["let it in if it waits in the lobby"]} />
        </MenuHead>
      );
    case "RECORDING":
      if (recording.endRequested) {
        return (
          <MenuHead icon={<Turning />}>
            Ending
            <Dim parts={["the notes follow in a minute or two"]} />
          </MenuHead>
        );
      }
      return (
        <MenuHead icon={<span aria-hidden="true" className="size-1.5 animate-pulse rounded-full bg-u-offlimits" />}>
          Recording <ElapsedClock since={recording.recordingStartedAt ?? recording.startsAt} />
        </MenuHead>
      );
    case "PROCESSING":
      return <MenuHead icon={<Turning />}>Writing notes…</MenuHead>;
    case "DONE":
      return (
        <MenuHead icon={<Icon d={ICONS.checkCircle} size={13} className="text-u-direct" />}>
          Notes filed
          <Dim parts={[changesStepOf(recording).words]} />
        </MenuHead>
      );
    case "FAILED":
      return (
        <MenuHead icon={<Icon d={ICONS.warning} size={13} className="text-u-signal" />}>
          {recording.recordingStartedAt !== null || recording.retryable ? "No notes" : "Not recorded"}
          <Dim parts={[failureWordsOf(recording.failureReason)]} />
        </MenuHead>
      );
    case "CANCELLED":
      return null;
  }
}

type ChangesAction = "review" | "view" | "ask" | null;

/** Where the call's changes have got to once its notes are filed, and the one thing left to do about them. */
function changesStepOf(recording: NotetakerRecording): { words: string | null; action: ChangesAction } {
  if (recording.suggesting) return { words: "finding changes…", action: null };
  if (!recording.suggestionsAsked) return { words: null, action: recording.hasTranscript ? "ask" : null };
  if (recording.pendingSuggestions > 0) {
    return { words: `${countOf(recording.pendingSuggestions, "change")} to review`, action: "review" };
  }
  if (recording.foundSuggestions === 0) return { words: "no changes found", action: null };
  return {
    words: recording.appliedSuggestions > 0 ? `${countOf(recording.appliedSuggestions, "change")} applied` : "no changes applied",
    action: "view",
  };
}

function countOf(count: number, noun: string): string {
  return count === 1 ? `1 ${noun}` : `${count} ${noun}s`;
}

function MenuHead({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <p className="mb-1 flex items-start gap-2 border-b border-u-border px-2 pb-2 pt-1 text-note text-u-text2 last:mb-0 last:border-b-0 last:pb-1">
      <span className="grid h-[1lh] w-3.5 flex-none place-items-center">{icon}</span>
      <span className="min-w-0">{children}</span>
    </p>
  );
}

function Dim({ parts }: { parts: (string | null)[] }) {
  const shown = parts.filter((part): part is string => part !== null);
  if (shown.length === 0) return null;
  return (
    <>
      {" "}
      <span className="text-u-text3">· {shown.join(" · ")}</span>
    </>
  );
}

function Turning() {
  return (
    <span
      aria-hidden="true"
      className="size-3 flex-none animate-spin rounded-full border-2 border-u-accent-ring border-t-u-accent"
    />
  );
}

/** How long the call has been recorded, ticking each second as the copilot's window does. */
function ElapsedClock({ since }: { since: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="font-mono tabular-nums text-u-text3">{elapsedSince(since, now)}</span>;
}

function MenuItem({
  icon,
  tone,
  onSelect,
  children,
}: {
  icon: string;
  tone?: "danger";
  onSelect: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onSelect}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start text-note hover:bg-u-raised",
        tone === "danger" ? "text-u-offlimits" : "text-u-text",
      )}
    >
      <Icon d={icon} size={13} className="flex-none opacity-80" />
      {children}
    </button>
  );
}

const ICON_ACTION =
  "relative grid size-[26px] place-items-center rounded-u-control border border-u-border text-u-text3 transition-colors focus-visible:outline-2 focus-visible:outline-u-accent";
