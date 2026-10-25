import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { AppIcon, Button } from "../../../../components/ui";
import { Avatar } from "../../../../components/ui/Avatar";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
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

type CopilotTab = "live" | "plan" | "transcript";

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
  const [tab, setTab] = useState<CopilotTab>("live");
  const [isMuted, setMuted] = useState(false);
  const [isMenuOpen, setMenuOpen] = useState(false);
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
      <span className="truncate">
        {copilot
          ? // The drawer already names the person; a window over the call has nothing else to say who it is.
            [copilot.title ?? "Recorded call", variant === "window" ? copilot.candidateName : null]
              .filter(Boolean)
              .join(" · ")
          : "Interview copilot"}
      </span>
      {copilot && !isLive && isCallOn(copilot) && <span className="text-meta text-u-text3">Reconnecting…</span>}
      {copilot && copilot.status === "RECORDING" && (
        <span className="ms-auto flex-none font-mono text-meta tabular-nums text-u-text3">
          {elapsedSince(copilot.startsAt, now)}
        </span>
      )}
    </>
  );
  const viewers = (
    <span className="flex items-center">
      {(copilot?.viewers ?? [])
        .filter((viewer) => !viewer.you)
        .map((viewer) => (
          <span key={viewer.userId} className="-me-1.5 rounded-full ring-2 ring-u-surface">
            <Avatar
              id={viewer.userId}
              name={viewer.name ?? "A teammate"}
              size="xs"
              title={`${viewer.name ?? "A teammate"} is watching`}
            />
          </span>
        ))}
      {copilot?.viewers
        .filter((viewer) => viewer.you)
        .map((viewer) => (
          <span key={viewer.userId} className="ms-2 rounded-full ring-2 ring-u-surface">
            <Avatar
              id={viewer.userId}
              name={viewer.name ?? "You"}
              size="sm"
              title={`${viewer.name ?? "You"} (you)`}
            />
          </span>
        ))}
    </span>
  );
  const menu = (
    <span className="relative">
      <button
        type="button"
        aria-label="More"
        aria-haspopup="menu"
        aria-expanded={isMenuOpen}
        onClick={() => setMenuOpen((open) => !open)}
        className="grid size-7 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text"
      >
        <Icon d={ICONS.more} size={16} />
      </button>
      {isMenuOpen && (
        <span
          role="menu"
          className="absolute end-0 top-8 z-20 flex w-52 flex-col rounded-u-card border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
        >
          <MenuItem
            onSelect={() => {
              setMuted((muted) => !muted);
              setMenuOpen(false);
            }}
          >
            {isMuted ? "Unmute suggestions" : "Mute suggestions"}
          </MenuItem>
          {onShowInUncava && (
            <MenuItem
              onSelect={() => {
                setMenuOpen(false);
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
                tone="danger"
                onSelect={() => {
                  setMenuOpen(false);
                  setConfirmingStop(true);
                }}
              >
                Stop copilot
              </MenuItem>
              <span className="px-2 pb-1 text-meta text-u-text3">
                Stops live suggestions for everyone on this call. The recording continues.
              </span>
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
      {variant === "tab" ? (
        <div className="flex flex-none flex-wrap items-center gap-x-3 gap-y-2 border-b border-u-border py-2.5">
          <div role="tablist" aria-label="Copilot views" className="inline-flex rounded-u-control border border-u-border bg-u-raised p-0.5">
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
          <span className="ms-auto flex min-w-0 items-center gap-2">
            <span className="flex min-w-0 items-center gap-2 text-note text-u-text2">{callLine}</span>
            {viewers}
            {onPopOut && (
              <Button variant="secondary" size="xs" onClick={onPopOut}>
                <Icon d={ICONS.externalLink} size={12} />
                Pop out
              </Button>
            )}
            {menu}
          </span>
        </div>
      ) : (
        <>
          <header className="flex flex-none items-center gap-2 border-b border-u-border px-3 py-2">
            <AppIcon className="h-5" />
            <h1 className="truncate text-body font-semibold">Interview copilot</h1>
            <span className="ms-auto" />
            {viewers}
            {menu}
            <button
              type="button"
              aria-label="Close window"
              onClick={onClose}
              className="grid size-7 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text"
            >
              <Icon d={ICONS.close} size={15} />
            </button>
          </header>
          <div className="flex flex-none flex-col gap-2 px-3 pt-2">
            <p className="flex min-w-0 items-center gap-2 text-note text-u-text2">{callLine}</p>
            <div role="tablist" aria-label="Copilot views" className="flex gap-1 border-b border-u-border">
              <TabButton current={tab} value="live" onSelect={setTab}>
                Live
              </TabButton>
              <TabButton current={tab} value="plan" onSelect={setTab}>
                Plan
                {planCount}
              </TabButton>
              <TabButton current={tab} value="transcript" onSelect={setTab}>
                Transcript
              </TabButton>
            </div>
          </div>
        </>
      )}

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
                isMuted={isMuted}
                onAct={act}
                ids={{ projectId, candidateId, recordingId }}
              />
            )}
            {tab === "plan" && <PlanTab copilot={copilot} onAct={act} ids={{ projectId, candidateId, recordingId }} />}
            {tab === "transcript" && <TranscriptTab lines={lines} speaking={copilot.speaking} />}
          </>
        )}
      </div>

      <footer
        className={cn(
          "flex flex-none items-center gap-2 border-t border-u-border py-1.5 text-meta text-u-text3",
          variant === "window" && "px-3",
        )}
      >
        <span>{footerLineOf(copilot, isMuted)}</span>
        <span className="ms-auto">Only you see this</span>
      </footer>

      {isConfirmingStop && (
        <div className="absolute inset-0 z-30 grid place-items-center bg-u-scrim p-4">
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
  isMuted,
  onAct,
  ids,
}: {
  copilot: Copilot;
  lines: CopilotLine[];
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
        <TranscriptLines lines={lines.slice(-3)} speaking={copilot.speaking} empty="Nobody has spoken yet." />
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

function TranscriptTab({ lines, speaking }: { lines: CopilotLine[]; speaking: CopilotLine[] }) {
  const end = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    end.current?.scrollIntoView?.({ block: "end" });
  }, [lines.length, speaking]);
  return (
    <>
      <TranscriptLines lines={lines} speaking={speaking} empty="The transcript appears here as people speak." />
      <span ref={end} />
    </>
  );
}

function TranscriptLines({ lines, speaking, empty }: { lines: CopilotLine[]; speaking: CopilotLine[]; empty: string }) {
  if (lines.length === 0 && speaking.length === 0) return <p className="text-note text-u-text3">{empty}</p>;
  return (
    <ol className="flex flex-col gap-1.5">
      {lines.map((line) => (
        <li key={line.seq} className="text-note">
          <SpeakerName line={line} />: {line.text}
        </li>
      ))}
      {speaking.map((line) => (
        <li key={`speaking-${line.seq}`} className="text-note text-u-text3">
          <SpeakerName line={line} />: {line.text}
          <span aria-hidden="true" className="ms-0.5 animate-pulse">
            ▍
          </span>
        </li>
      ))}
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
  return (
    <div className="rounded-u-control bg-u-raised px-2.5 py-2 text-note text-u-text2">
      <b className="font-semibold text-u-text">Call ended.</b>{" "}
      {copilot.status === "PROCESSING" ? "Notes on the way." : "Notes are filed from the recording."}{" "}
      {open.length > 0 ? `Not covered: ${open.join(" · ")}` : "Everything was covered."}
    </div>
  );
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
        "rounded-u-control px-3 py-1 text-note font-medium",
        current === value ? "bg-u-surface text-u-text shadow-u-e1" : "text-u-text3 hover:text-u-text",
      )}
    >
      {children}
    </button>
  );
}

function TabButton({
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
        "-mb-px border-b-2 px-2.5 py-1.5 text-note",
        current === value ? "border-u-accent text-u-text" : "border-transparent text-u-text3 hover:text-u-text",
      )}
    >
      {children}
    </button>
  );
}

function MenuItem({ onSelect, tone, children }: { onSelect: () => void; tone?: "danger"; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onSelect}
      className={cn(
        "rounded-u-control px-2 py-1.5 text-start text-note hover:bg-u-raised",
        tone === "danger" && "text-u-offlimits",
      )}
    >
      {children}
    </button>
  );
}

function footerLineOf(copilot: Copilot | null, isMuted: boolean): string {
  if (!copilot) return "Connecting…";
  if (copilot.liveStatus === "STOPPED") return "Copilot stopped · recording continues";
  if (!isCallOn(copilot)) return "Call ended · notes on the way";
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
