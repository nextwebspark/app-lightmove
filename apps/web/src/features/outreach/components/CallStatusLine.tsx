import { type ReactNode, useEffect, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import type { NotetakerRecording } from "../api/notetakerApi";
import { elapsedSince } from "../lib/copilotWords";
import { failureWordsOf } from "../lib/notetakerChip";
import { useNotetakerActions } from "../lib/useNotetakerActions";
import { questionsReadyLabelOf } from "../lib/useQuestionsReady";

/**
 * The one line under a call once anything is set for it, in plain words: before the call what the Notetaker, the
 * copilot and the questions will do; in it, how long it has recorded; after it, the notes and then the changes they
 * found, each step arriving with no press. At most one strong button, and only when it is time for it: End & file
 * notes while recording, Review while changes wait. A call nobody set anything on has no line at all.
 */
export function CallStatusLine({
  projectId,
  candidateId,
  recording,
  questionsReady,
  firstName,
  onOpenPanel,
  onShowTranscript,
  onShowChanges,
  onOpenCopilot,
}: {
  projectId: string;
  candidateId: string;
  /** The call's latest bot, where one was ever sent. */
  recording: NotetakerRecording | null;
  /** Questions saved for the call, or null where none are. */
  questionsReady: number | null;
  firstName: string;
  /** Opens what the call offers to change; absent where it offers nothing. */
  onOpenPanel?: () => void;
  onShowTranscript: (recordingId: string) => void;
  /** Opens what the transcript changes on the record: the review while changes wait, what was done after. */
  onShowChanges: (recordingId: string) => void;
  /** Opens the live copilot's window; called straight from the click, which the window needs. */
  onOpenCopilot: (recordingId: string) => void;
}) {
  const actions = useNotetakerActions(projectId, candidateId, firstName);
  const [isEnding, setIsEnding] = useState(false);
  const bot = recording?.status === "CANCELLED" ? null : recording;
  const questions = questionsReady === null ? null : questionsReadyLabelOf(questionsReady);

  if (!bot) {
    if (!questions) return null;
    return (
      <Line>
        <Lead onClick={onOpenPanel} icon={<Icon d={ICONS.checklist} size={13} className="text-u-direct" />}>
          {questions}
        </Lead>
      </Line>
    );
  }

  const copilotLink = bot.liveCopilot ? (
    <button type="button" onClick={() => onOpenCopilot(bot.id)} aria-label="Open copilot" className={ACCENT_LINK}>
      Copilot
      <Icon d={ICONS.externalLink} size={11} />
    </button>
  ) : null;

  switch (bot.status) {
    case "SCHEDULED":
    case "JOINING":
      if (bot.status === "SCHEDULED" && bot.joinAt !== null) {
        return (
          <Line>
            <Lead onClick={onOpenPanel} icon={<Icon d={ICONS.mic} size={13} className="text-u-accent" />}>
              Notetaker joins at start
              <Dim parts={[bot.liveCopilot ? "Live copilot" : null, questions]} />
            </Lead>
          </Line>
        );
      }
      return (
        <Line>
          <Lead onClick={onOpenPanel} icon={<Turning />}>
            Notetaker joining
            <Dim parts={["let it in if it waits in the lobby"]} />
          </Lead>
          {copilotLink && <Actions>{copilotLink}</Actions>}
        </Line>
      );
    case "RECORDING":
      if (bot.endRequested) {
        return (
          <Line>
            <Lead icon={<Turning />}>
              Ending
              <Dim parts={["the notes follow in a minute or two"]} />
            </Lead>
          </Line>
        );
      }
      return (
        <Line>
          <Lead
            onClick={onOpenPanel}
            icon={<span aria-hidden="true" className="size-1.5 animate-pulse rounded-full bg-u-offlimits" />}
          >
            Recording <ElapsedClock since={bot.recordingStartedAt ?? bot.startsAt} />
          </Lead>
          <Actions>
            {copilotLink}
            <Button size="xs" disabled={actions.end.isPending} onClick={() => setIsEnding(true)}>
              End &amp; file notes
            </Button>
          </Actions>
          <ConfirmDialog
            open={isEnding}
            tone="primary"
            title="End the recording and file the notes?"
            confirmLabel="End & file notes"
            cancelLabel="Keep recording"
            pending={actions.end.isPending}
            onConfirm={() => actions.end.mutate(bot.id, { onSettled: () => setIsEnding(false) })}
            onClose={() => setIsEnding(false)}
          >
            <p>The Notetaker leaves the call now and writes up what it heard. It can&apos;t rejoin this call.</p>
          </ConfirmDialog>
        </Line>
      );
    case "PROCESSING":
      return (
        <Line>
          <Lead icon={<Turning />}>Writing notes…</Lead>
          {copilotLink && <Actions>{copilotLink}</Actions>}
        </Line>
      );
    case "DONE": {
      const next = changesStepOf(bot);
      return (
        <Line>
          <Lead icon={<Icon d={ICONS.checkCircle} size={13} className="text-u-direct" />}>
            Notes filed
            <Dim parts={[next.words]} />
          </Lead>
          <Actions>
            {bot.hasTranscript && (
              <button type="button" onClick={() => onShowTranscript(bot.id)} className={QUIET_LINK}>
                Transcript
              </button>
            )}
            {next.action === "review" && (
              <Button size="xs" onClick={() => onShowChanges(bot.id)}>
                Review
              </Button>
            )}
            {(next.action === "view" || next.action === "ask") && (
              <button type="button" onClick={() => onShowChanges(bot.id)} className={ACCENT_LINK}>
                {next.action === "view" ? "Changes" : "Suggest changes"}
              </button>
            )}
          </Actions>
        </Line>
      );
    }
    case "FAILED":
      return (
        <Line>
          <Lead onClick={onOpenPanel} icon={<Icon d={ICONS.warning} size={13} className="text-u-signal" />}>
            {bot.recordingStartedAt !== null || bot.retryable ? "No notes" : "Not recorded"}
            <Dim parts={[failureWordsOf(bot.failureReason)]} />
          </Lead>
          {bot.retryable && (
            <Actions>
              <Button
                size="xs"
                variant="secondary"
                disabled={actions.retry.isPending}
                onClick={() => actions.retry.mutate(bot.id)}
              >
                Try again
              </Button>
            </Actions>
          )}
        </Line>
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

function Line({ children }: { children: ReactNode }) {
  return <div className="mt-1.5 flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1 text-note">{children}</div>;
}

/** The line's words: a button where they open the call's panel, plain text where there is none to open. */
function Lead({ icon, onClick, children }: { icon: ReactNode; onClick?: () => void; children: ReactNode }) {
  const body = (
    <>
      <span className="grid w-3.5 flex-none place-items-center">{icon}</span>
      <span className="min-w-0">{children}</span>
    </>
  );
  if (!onClick) return <span className="inline-flex min-w-0 items-center gap-2 text-u-text2">{body}</span>;
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex min-w-0 items-center gap-2 text-start text-u-text2 hover:text-u-text"
    >
      {body}
    </button>
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

function Actions({ children }: { children: ReactNode }) {
  return <span className="ms-auto flex flex-none items-center gap-3">{children}</span>;
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

const QUIET_LINK = "flex-none text-note font-medium text-u-text3 hover:text-u-text";

const ACCENT_LINK =
  "inline-flex flex-none items-center gap-1 text-note font-medium text-u-accent hover:underline hover:underline-offset-2";
