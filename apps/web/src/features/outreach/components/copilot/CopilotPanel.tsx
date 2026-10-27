import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button } from "../../../../components/ui";
import { Avatar } from "../../../../components/ui/Avatar";
import { requestBlob } from "../../../../lib/apiClient";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { useEscapeKey } from "../../../../lib/useEscapeKey";
import type { Copilot, CopilotLine } from "../../api/notetakerApi";
import * as notetakerApi from "../../api/notetakerApi";
import {
  COVERAGE_LABEL,
  copilotNoticeOf,
  elapsedSince,
  GAP_LABEL,
  isCallOn,
  isWaitingToJoin,
  uncoveredOf,
} from "../../lib/copilotWords";
import { useCopilot } from "../../lib/useCopilot";
import { type CopilotView as CopilotTab, useCopilotView } from "../../lib/copilotView";

/** How long the ended view stays up in a window of its own before it closes itself. */
const CLOSE_AFTER_END_MS = 60_000;

/**
 * The live copilot of one call (`Position.dc.html`'s Interview copilot): competency coverage, the record's gaps, what to
 * ask next, the plan and the transcript, read as the call goes. Drawn in a floating window over the call, a popup
 * where the browser has no floating windows, or the executive drawer. Everything it draws stays in this tree — no
 * portal, no toast — because in a floating window the opener's `document.body` is another window.
 */
export function CopilotPanel({
  projectId,
  candidateId,
  recordingId,
  variant,
  onClose,
  onShowInUncava,
  onPopOut,
  onNudgeCount,
  notice,
}: {
  projectId: string;
  candidateId: string;
  recordingId: string;
  /** `window`: its own window, which closes itself a minute after the call ends; `tab`: the executive drawer's tab. */
  variant: "window" | "tab";
  /** The window's own close; the tab has none. */
  onClose?: () => void;
  /** The floating window's way back into the drawer. */
  onShowInUncava?: () => void;
  /** The tab's way out into a window over the call; called straight from the click, which the window needs. */
  onPopOut?: () => void;
  /** The suggestions waiting to be used, for the drawer's tab to count while another tab is open. */
  onNudgeCount?: (count: number) => void;
  /** A line the host wants said once, such as a popup that cannot stay on top. */
  notice?: string;
}) {
  const { copilot, lines, isLive, error, read, setCopilot } = useCopilot(projectId, candidateId, recordingId);
  const [{ view: tab, isMuted }, updateView] = useCopilotView(recordingId);
  const setTab = (view: CopilotTab) => updateView({ view });
  const menuState = useCopilotMenu();
  const photoUrl = useExecutivePhoto(projectId, candidateId);
  const faceOf = (line: CopilotLine): Face => {
    if (!line.team) return { id: candidateId, src: photoUrl };
    const viewer = copilot?.viewers.find((held) => held.name === line.speaker);
    return { id: viewer?.userId ?? line.speaker, src: viewer?.avatarUrl ?? null };
  };
  const [isConfirmingStop, setConfirmingStop] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isStopping, setStopping] = useState(false);
  const now = useNow();

  const hasEnded = copilot !== null && !isCallOn(copilot);
  const waitingNudges = isMuted ? 0 : (copilot?.nudges.length ?? 0);
  useEffect(() => {
    onNudgeCount?.(waitingNudges);
  }, [waitingNudges, onNudgeCount]);
  useEffect(() => {
    if (!hasEnded || variant !== "window" || !onClose) return;
    const timer = window.setTimeout(onClose, CLOSE_AFTER_END_MS);
    return () => window.clearTimeout(timer);
  }, [hasEnded, variant, onClose]);

  const act = async (action: () => Promise<unknown>) => {
    setActionError(null);
    try {
      await action();
      await read();
    } catch (failed) {
      setActionError(messageFor(failed));
    }
  };
  const handleStop = async () => {
    setStopping(true);
    setActionError(null);
    try {
      setCopilot(await notetakerApi.stopCopilot(projectId, candidateId, recordingId));
      setConfirmingStop(false);
    } catch (failed) {
      setActionError(messageFor(failed));
    } finally {
      setStopping(false);
    }
  };

  const planCount = copilot && copilot.plan.length > 0 && (
    <span className="ms-1 font-mono text-meta text-u-text3">
      {copilot.plan.filter((question) => question.asked).length}/{copilot.plan.length}
    </span>
  );
  const callLine = (
    <>
      <span
        aria-hidden="true"
        className={cn(
          "size-2 flex-none rounded-full",
          copilot && isCallOn(copilot) && !isWaitingToJoin(copilot)
            ? isLive
              ? "animate-pulse bg-u-offlimits"
              : "bg-u-text3"
            : "invisible",
        )}
      />
      <span className="truncate">{copilot ? (copilot.title ?? "Recorded call") : "Interview copilot"}</span>
      {copilot && copilot.status === "RECORDING" && (
        <span className="flex-none font-mono tabular-nums">
          {elapsedSince(copilot.recordingStartedAt ?? copilot.startsAt, now)}
        </span>
      )}
      {copilot && !isLive && isCallOn(copilot) && <span className="flex-none text-u-signal">Reconnecting…</span>}
    </>
  );
  const menu = (
    <span ref={menuState.rootRef} className="relative">
      <button
        ref={menuState.triggerRef}
        type="button"
        aria-label="Copilot options"
        aria-haspopup="menu"
        aria-expanded={menuState.open}
        onClick={menuState.toggle}
        className={cn(
          "grid size-7 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text",
          menuState.open && "bg-u-raised text-u-text",
        )}
      >
        <Icon d={ICONS.more} size={16} />
      </button>
      {menuState.open && (
        <span
          ref={menuState.menuRef}
          role="menu"
          aria-label="Copilot"
          onKeyDown={menuState.handleKeyDown}
          className="absolute end-0 top-8 z-20 flex w-60 animate-fade-up flex-col rounded-u-card border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
        >
          <MenuItem
            icon={ICONS.bell}
            onSelect={() => {
              updateView({ isMuted: !isMuted });
              menuState.close(true);
            }}
          >
            {isMuted ? "Unmute suggestions" : "Mute suggestions"}
          </MenuItem>
          {onShowInUncava && (
            <MenuItem
              icon={ICONS.arrowLeft}
              onSelect={() => {
                menuState.close();
                onShowInUncava();
              }}
            >
              Show in Uncava
            </MenuItem>
          )}
          {copilot?.mayStop && copilot.liveStatus === "LISTENING" && isCallOn(copilot) && (
            <>
              <span className="my-1 h-px bg-u-border" />
              <MenuItem
                icon={ICONS.ban}
                tone="danger"
                hint="Stops suggestions for everyone. The recording continues."
                onSelect={() => {
                  menuState.close();
                  setConfirmingStop(true);
                }}
              >
                Stop copilot
              </MenuItem>
            </>
          )}
        </span>
      )}
    </span>
  );

  return (
    <section
      aria-label="Interview copilot"
      className="relative flex h-full min-h-0 flex-col overflow-hidden bg-u-surface text-u-text"
    >
      {/* The window heads with the executive; the drawer already does, so its tab heads with the copilot itself. */}
      <header className={cn("flex flex-none items-center gap-2.5 pb-2.5 pt-3", variant === "window" && "px-3")}>
        {variant === "window" ? (
          <Avatar id={candidateId} name={copilot?.candidateName ?? "Executive"} src={photoUrl} size="lg" />
        ) : (
          <span aria-hidden="true" className="grid size-7.5 flex-none place-items-center rounded-full bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.mic} size={15} />
          </span>
        )}
        <div className="flex min-w-0 flex-1 flex-col">
          {variant === "window" ? (
            <h1 className="truncate text-body font-semibold">{copilot?.candidateName ?? "Interview copilot"}</h1>
          ) : (
            <h2 className="truncate text-body font-semibold">Interview copilot</h2>
          )}
          <p className="flex min-w-0 items-center gap-1.5 text-meta text-u-text3">{callLine}</p>
        </div>
        {onPopOut && (
          <Button variant="secondary" size="xs" onClick={onPopOut}>
            <Icon d={ICONS.externalLink} size={12} />
            Pop out
          </Button>
        )}
        {menu}
        {variant === "window" && (
          <button
            type="button"
            aria-label="Close window"
            onClick={onClose}
            className="grid size-7 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={15} />
          </button>
        )}
      </header>
      <div className={cn("flex-none border-b border-u-border pb-2.5", variant === "window" && "px-3")}>
        <div
          role="tablist"
          aria-label="Copilot views"
          className="grid grid-cols-3 rounded-u-control border border-u-border bg-u-raised p-0.5"
        >
          <ViewButton current={tab} value="live" onSelect={setTab}>
            Live
          </ViewButton>
          <ViewButton current={tab} value="plan" onSelect={setTab}>
            Plan
            {planCount}
          </ViewButton>
          <ViewButton current={tab} value="transcript" onSelect={setTab}>
            Transcript
          </ViewButton>
        </div>
      </div>

      <div className={cn("flex min-h-0 flex-1 flex-col gap-3.5 overflow-y-auto", variant === "tab" ? "py-4" : "p-3")}>
        {notice && <Notice>{notice}</Notice>}
        {actionError && (
          <p role="alert" className="text-note text-u-offlimits">
            {actionError}
          </p>
        )}
        {!copilot && !error && <p className="text-note text-u-text3">Opening the copilot…</p>}
        {!copilot && error !== null && (
          <p role="alert" className="text-note text-u-text2">
            {messageFor(error)}
          </p>
        )}
        {copilot && (
          <>
            {copilotNoticeOf(copilot) && <Notice>{copilotNoticeOf(copilot)}</Notice>}
            {hasEnded && <EndedSummary copilot={copilot} />}
            {tab === "live" && (
              <LiveTab
                copilot={copilot}
                lines={lines}
                faceOf={faceOf}
                isMuted={isMuted}
                onAct={act}
                ids={{ projectId, candidateId, recordingId }}
              />
            )}
            {tab === "plan" && <PlanTab copilot={copilot} onAct={act} ids={{ projectId, candidateId, recordingId }} />}
            {tab === "transcript" && (
              <TranscriptTab
                lines={lines}
                speaking={copilot.speaking}
                faceOf={faceOf}
                empty={isCallOn(copilot) ? "The transcript appears here as people speak." : LINES_GONE}
              />
            )}
          </>
        )}
      </div>

      <footer
        className={cn(
          "flex flex-none items-center gap-2 border-t border-u-border py-1.5 text-meta text-u-text3",
          variant === "window" && "px-3",
        )}
      >
        <span className="truncate">{footerLineOf(copilot, isMuted)}</span>
        <Viewers viewers={copilot?.viewers ?? []} />
      </footer>

      {isConfirmingStop && (
        <div
          className="absolute inset-0 z-30 grid place-items-center bg-u-scrim p-4"
          onClick={(event) => {
            if (event.target === event.currentTarget && !isStopping) setConfirmingStop(false);
          }}
        >
          <div
            role="alertdialog"
            aria-labelledby="copilot-stop-title"
            className="flex max-w-[300px] flex-col gap-2.5 rounded-u-card border border-u-border-strong bg-u-surface p-4 shadow-u-e3"
          >
            <h2 id="copilot-stop-title" className="text-body font-semibold">
              Stop the copilot for this call?
            </h2>
            <p className="text-note text-u-text2">
              Live suggestions stop for everyone watching. Coverage stays as it is now. The Notetaker keeps recording
              and the notes are still filed after the call.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="secondary" size="xs" onClick={() => setConfirmingStop(false)}>
                Keep it on
              </Button>
              <Button variant="danger" size="xs" loading={isStopping} onClick={() => void handleStop()}>
                Stop copilot
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

interface CopilotIds {
  projectId: string;
  candidateId: string;
  recordingId: string;
}

function LiveTab({
  copilot,
  lines,
  faceOf,
  isMuted,
  onAct,
  ids,
}: {
  copilot: Copilot;
  lines: CopilotLine[];
  faceOf: (line: CopilotLine) => Face;
  isMuted: boolean;
  onAct: (action: () => Promise<unknown>) => Promise<void>;
  ids: CopilotIds;
}) {
  const [openCompetency, setOpenCompetency] = useState<string | null>(null);
  const opened = copilot.coverage.find((line) => line.competency === openCompetency) ?? null;
  const isOn = isCallOn(copilot) && copilot.liveStatus === "LISTENING";
  return (
    <>
      {isWaitingToJoin(copilot) && (
        <p className="text-note text-u-text3">
          Waiting for the Notetaker to be let into the call. Coverage below starts from what is on file.
        </p>
      )}
      <div className="flex flex-col gap-2">
        <span className="type-label text-u-text3">Competencies</span>
        {copilot.coverage.length === 0 ? (
          <p className="text-note text-u-text3">The brief has no competencies yet.</p>
        ) : (
          <div className="flex flex-wrap gap-1.5">
            {copilot.coverage.map((line) => (
              <button
                key={`${line.panel}-${line.competency}`}
                type="button"
                aria-expanded={openCompetency === line.competency}
                title={`${line.competency}: ${COVERAGE_LABEL[line.state]}`}
                onClick={() => setOpenCompetency((open) => (open === line.competency ? null : line.competency))}
                className={cn(
                  "inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-note font-medium transition-colors",
                  line.state === "EVIDENCED" && "border-transparent bg-u-direct-tint text-u-direct",
                  line.state === "TOUCHED" && "border-transparent bg-u-signal-tint text-u-signal",
                  line.state === "NOT_YET" && "border-u-border-strong text-u-text3",
                )}
              >
                <span aria-hidden="true" className="size-1.5 flex-none rounded-full bg-current" />
                <span className="truncate">{line.competency}</span>
              </button>
            ))}
          </div>
        )}
        {opened && (
          <p className="border-s-2 border-u-border-strong ps-2.5 text-note text-u-text2">
            {opened.quote ? (
              <>
                {opened.at && <span className="me-1.5 font-mono text-meta text-u-accent">{opened.at}</span>}“
                {opened.quote}”
              </>
            ) : (
              <>
                {opened.competency}: {COVERAGE_LABEL[opened.state].toLowerCase()}
              </>
            )}
          </p>
        )}
      </div>

      {copilot.gaps.length > 0 && (
        <div className="flex flex-col gap-2">
          <span className="type-label text-u-text3">To find out</span>
          <ul className="grid grid-cols-3 gap-x-2 gap-y-1">
            {copilot.gaps.map((gap) => (
              <li
                key={gap.gap}
                className={cn("flex items-center gap-1.5 text-note", gap.heard ? "text-u-text" : "text-u-text3")}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    "grid size-3.5 flex-none place-items-center rounded-sm border text-eyebrow text-white",
                    gap.heard ? "border-u-direct bg-u-direct" : "border-u-border-strong",
                  )}
                >
                  {gap.heard ? "✓" : ""}
                </span>
                {GAP_LABEL[gap.gap]}
                <span className="sr-only">{gap.heard ? "said" : "not said yet"}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-col gap-2">
        <span className="type-label text-u-text3">Ask next</span>
        {isMuted ? (
          <p className="text-note text-u-text3">Suggestions are muted. Coverage keeps updating.</p>
        ) : !isOn ? (
          <p className="text-note text-u-text3">No more suggestions for this call.</p>
        ) : copilot.nudges.length === 0 ? (
          <p className="text-note text-u-text3">Nothing to suggest right now.</p>
        ) : (
          <ul className="flex flex-col gap-2" aria-live="polite">
            {copilot.nudges.map((nudge) => (
              <li
                key={nudge.id}
                className="flex animate-fade-up flex-col gap-1 rounded-u-card border border-u-border-strong bg-u-bg p-2.5"
              >
                <p className="text-body font-medium">{nudge.text}</p>
                {nudge.reason && <p className="text-meta text-u-text3">{nudge.reason}</p>}
                <span className="flex justify-end gap-1.5">
                  <Button
                    variant="ghost"
                    size="xs"
                    onClick={() =>
                      void onAct(() =>
                        notetakerApi.decideNudge(ids.projectId, ids.candidateId, ids.recordingId, nudge.id, "dismiss"),
                      )
                    }
                  >
                    Dismiss
                  </Button>
                  <Button
                    variant="secondary"
                    size="xs"
                    onClick={() =>
                      void onAct(() =>
                        notetakerApi.decideNudge(ids.projectId, ids.candidateId, ids.recordingId, nudge.id, "used"),
                      )
                    }
                  >
                    Used
                  </Button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <span className="type-label text-u-text3">Last said</span>
        <TranscriptLines
          lines={lines.slice(-3)}
          speaking={copilot.speaking}
          faceOf={faceOf}
          empty={isCallOn(copilot) ? "Nobody has spoken yet." : LINES_GONE}
        />
      </div>
    </>
  );
}

function PlanTab({
  copilot,
  onAct,
  ids,
}: {
  copilot: Copilot;
  onAct: (action: () => Promise<unknown>) => Promise<void>;
  ids: CopilotIds;
}) {
  if (copilot.plan.length === 0) {
    return (
      <p className="text-note text-u-text3">
        No questions were prepared for this call. Prepare questions from the call's row before the next one.
      </p>
    );
  }
  const sections = [
    { section: "FIT", title: "Fit for the role" },
    { section: "GAP", title: "Fill the gaps" },
    { section: "CLARIFY", title: "Clarify" },
  ] as const;
  return (
    <>
      {sections.map(({ section, title }) => {
        const questions = copilot.plan.filter((question) => question.section === section);
        if (questions.length === 0) return null;
        return (
          <div key={section} className="flex flex-col gap-1.5">
            <span className="type-label text-u-text3">{title}</span>
            {questions.map((question) => (
              <label key={question.id} className="flex cursor-pointer items-start gap-2 text-note">
                <input
                  type="checkbox"
                  checked={question.asked}
                  onChange={(event) =>
                    void onAct(() =>
                      notetakerApi.markPlanQuestionAsked(
                        ids.projectId,
                        ids.candidateId,
                        ids.recordingId,
                        question.id,
                        event.target.checked,
                      ),
                    )
                  }
                  className="mt-0.5 size-3.5 flex-none accent-u-direct"
                />
                <span className={cn(question.asked && "text-u-text3")}>
                  {question.text}
                  {question.asked && <span className="ms-1 text-meta text-u-direct">asked</span>}
                </span>
              </label>
            ))}
          </div>
        );
      })}
    </>
  );
}

function TranscriptTab({
  lines,
  speaking,
  faceOf,
  empty,
}: {
  lines: CopilotLine[];
  speaking: CopilotLine[];
  faceOf: (line: CopilotLine) => Face;
  empty: string;
}) {
  const end = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    end.current?.scrollIntoView?.({ block: "end" });
  }, [lines.length, speaking]);
  return (
    <>
      <TranscriptLines
        lines={lines}
        speaking={speaking}
        faceOf={faceOf}
        empty={empty}
      />
      <span ref={end} />
    </>
  );
}

/** Who a line's face is: the executive's photo, or a teammate's own picture where their name is one watching. */
interface Face {
  id: string;
  src: string | null;
}

function TranscriptLines({
  lines,
  speaking,
  faceOf,
  empty,
}: {
  lines: CopilotLine[];
  speaking: CopilotLine[];
  faceOf: (line: CopilotLine) => Face;
  empty: string;
}) {
  if (lines.length === 0 && speaking.length === 0) return <p className="text-note text-u-text3">{empty}</p>;
  const row = (line: CopilotLine, isSpeaking: boolean) => {
    const face = faceOf(line);
    return (
      <li
        key={isSpeaking ? `speaking-${line.seq}` : line.seq}
        className={cn("flex items-start gap-2 text-note", isSpeaking && "text-u-text3")}
      >
        <Avatar id={face.id} name={line.speaker} src={face.src} size="sm" className="mt-px" />
        <span className="min-w-0">
          <SpeakerName line={line} />: {line.text}
          {isSpeaking && (
            <span aria-hidden="true" className="ms-0.5 animate-pulse">
              ▍
            </span>
          )}
        </span>
      </li>
    );
  };
  return (
    <ol className="flex flex-col gap-2">
      {lines.map((line) => row(line, false))}
      {speaking.map((line) => row(line, true))}
    </ol>
  );
}

function SpeakerName({ line }: { line: CopilotLine }) {
  return (
    <span className={cn("font-semibold", line.team && "text-u-accent")}>
      {line.speaker}
      {line.team && " (you)"}
    </span>
  );
}

function EndedSummary({ copilot }: { copilot: Copilot }) {
  const open = uncoveredOf(copilot);
  if (!hasRecorded(copilot)) {
    return (
      <div className="rounded-u-control bg-u-raised px-2.5 py-2 text-note text-u-text2">
        <b className="font-semibold text-u-text">The Notetaker left.</b> Nothing was recorded, so no notes are filed.
      </div>
    );
  }
  return (
    <div className="rounded-u-control bg-u-raised px-2.5 py-2 text-note text-u-text2">
      <b className="font-semibold text-u-text">Call ended.</b>{" "}
      {copilot.status === "PROCESSING" ? "Notes on the way." : "Notes are filed from the recording."}{" "}
      {open.length > 0 ? `Not covered: ${open.join(" · ")}` : "Everything was covered."}
    </div>
  );
}

/** The live lines go when the call ends; the call's transcript is in its ⋯ once its notes are filed. */
const LINES_GONE = "The live transcript isn't kept after the call.";

/** A removed bot, or one that never got in, recorded nothing: there are no notes to promise. */
function hasRecorded(copilot: Copilot): boolean {
  return copilot.status !== "CANCELLED" && copilot.status !== "FAILED";
}

function Notice({ children }: { children: React.ReactNode }) {
  return <p className="rounded-u-control bg-u-signal-tint px-2.5 py-1.5 text-note text-u-signal">{children}</p>;
}

/** The tab's views, a switch rather than a second row of tabs under the drawer's own. */
function ViewButton({
  current,
  value,
  onSelect,
  children,
}: {
  current: CopilotTab;
  value: CopilotTab;
  onSelect: (tab: CopilotTab) => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={current === value}
      onClick={() => onSelect(value)}
      className={cn(
        "rounded-u-control px-3 py-1 text-center text-note font-medium",
        current === value ? "bg-u-surface text-u-text shadow-u-e1" : "text-u-text3 hover:text-u-text",
      )}
    >
      {children}
    </button>
  );
}

function MenuItem({
  onSelect,
  icon,
  tone,
  hint,
  children,
}: {
  onSelect: () => void;
  icon: string;
  tone?: "danger";
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onSelect}
      className={cn(
        "flex items-start gap-2 rounded-u-control px-2 py-1.5 text-start text-note outline-none hover:bg-u-raised focus-visible:bg-u-raised",
        tone === "danger" ? "text-u-offlimits" : "text-u-text",
      )}
    >
      <Icon d={icon} size={14} className="mt-0.5 flex-none opacity-70" />
      <span className="flex flex-col">
        {children}
        {hint && <span className="text-meta text-u-text3">{hint}</span>}
      </span>
    </button>
  );
}

/** Whoever has this call's copilot open, faces first; the caller last, since they know they are here. */
function Viewers({ viewers }: { viewers: Copilot["viewers"] }) {
  if (viewers.length === 0) return null;
  const ordered = [...viewers.filter((viewer) => !viewer.you), ...viewers.filter((viewer) => viewer.you)];
  const others = ordered.filter((viewer) => !viewer.you);
  return (
    <span className="ms-auto flex flex-none items-center gap-1.5">
      {others.length > 0 && (
        <span>
          {others.length === 1 ? `${firstNameOf(others[0].name) ?? "A teammate"} is watching` : `${others.length} watching`}
        </span>
      )}
      <span className="flex items-center">
        {ordered.map((viewer) => (
          <span key={viewer.userId} className="-ms-1 rounded-full ring-2 ring-u-surface first:ms-0">
            <Avatar
              id={viewer.userId}
              name={viewer.name ?? (viewer.you ? "You" : "A teammate")}
              src={viewer.avatarUrl}
              size="xs"
              title={viewer.you ? `${viewer.name ?? "You"} (you)` : `${viewer.name ?? "A teammate"} is watching`}
            />
          </span>
        ))}
      </span>
    </span>
  );
}

function firstNameOf(name: string | null): string | null {
  return name?.trim().split(/\s+/)[0] || null;
}

/**
 * The ⋯ menu, closed by a click anywhere else, Escape, or the window losing focus — so a click on the call behind a
 * floating window closes it too. It listens on its own document: in a floating window that is not this page's.
 */
function useCopilotMenu() {
  const [open, setOpen] = useState(false);
  const [inOtherWindow, setInOtherWindow] = useState(false);
  const rootRef = useRef<HTMLSpanElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLSpanElement>(null);

  const close = useCallback((returnFocus = false) => {
    setOpen(false);
    if (returnFocus) triggerRef.current?.focus();
  }, []);

  useLayoutEffect(() => {
    setInOtherWindow((rootRef.current?.ownerDocument ?? document) !== document);
  }, []);
  // In the drawer, on the shared Escape stack, so Escape closes the menu and never the drawer beneath it.
  useEscapeKey(open && !inOtherWindow, () => close(true));

  useEffect(() => {
    if (!open) return;
    const doc = rootRef.current?.ownerDocument ?? document;
    const view = doc.defaultView ?? window;
    const handleOutside = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      close(true);
    };
    const handleBlur = () => setOpen(false);
    doc.addEventListener("pointerdown", handleOutside);
    if (doc !== document) doc.addEventListener("keydown", handleEscape);
    view.addEventListener("blur", handleBlur);
    menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    return () => {
      doc.removeEventListener("pointerdown", handleOutside);
      doc.removeEventListener("keydown", handleEscape);
      view.removeEventListener("blur", handleBlur);
    };
  }, [open, close]);

  const handleKeyDown = (event: KeyboardEvent<HTMLSpanElement>) => {
    if (event.key === "Tab") {
      close(true);
      return;
    }
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    const at = items.indexOf(event.currentTarget.ownerDocument.activeElement as HTMLElement);
    const next =
      event.key === "ArrowDown"
        ? items[(at + 1) % items.length]
        : event.key === "ArrowUp"
          ? items[(at - 1 + items.length) % items.length]
          : null;
    if (next) {
      event.preventDefault();
      next.focus();
    }
  };

  return { open, toggle: () => setOpen((wasOpen) => !wasOpen), close, rootRef, triggerRef, menuRef, handleKeyDown };
}

/**
 * The executive's stored photo as an object URL, or null for the initials. Fetched, since the route needs the bearer
 * token; the floating window shares this page's origin, so the URL draws there too.
 */
function useExecutivePhoto(projectId: string, candidateId: string): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    let disposed = false;
    let created: string | null = null;
    requestBlob(`/projects/${projectId}/candidates/${candidateId}/photo`)
      .then((blob) => {
        if (disposed) return;
        created = URL.createObjectURL(blob);
        setUrl(created);
      })
      .catch(() => {
        // No photo on file: the initials stand.
      });
    return () => {
      disposed = true;
      if (created) URL.revokeObjectURL(created);
      setUrl(null);
    };
  }, [projectId, candidateId]);
  return url;
}

function footerLineOf(copilot: Copilot | null, isMuted: boolean): string {
  if (!copilot) return "Connecting…";
  if (!isCallOn(copilot)) {
    if (!hasRecorded(copilot)) return "Notetaker left · nothing recorded";
    return copilot.status === "DONE" ? "Call ended · notes filed" : "Call ended · notes on the way";
  }
  if (copilot.liveStatus === "STOPPED") return "Copilot stopped · recording continues";
  if (isWaitingToJoin(copilot)) return "Waiting to be let in…";
  return isMuted ? "Suggestions muted" : "Listening";
}

/** The clock the elapsed time reads, ticking once a second. */
function useNow(): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}
