import { type ReactNode, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal, Toggle } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import type { Meeting } from "../api/meetingApi";
import type { NotetakerJoin, NotetakerRecording } from "../api/notetakerApi";
import { failureWordsOf, isNotetakerLive } from "../lib/notetakerChip";
import { meetingWhenOf, timeOfDayOf } from "../lib/meetingTimes";
import { useNotetakerActions } from "../lib/useNotetakerActions";
import { useQuestionsReady } from "../lib/useQuestionsReady";

/** A scheduled bot due sooner than this is left to join on its own; the server draws the same line. */
const JOIN_NOW_LEAD_MS = 2 * 60_000;

/**
 * Notetaker & questions, the call's panel behind its ⋯: one section per job, each with its own button. Opening it
 * again shows what is set — when the Notetaker joins, whether the copilot follows it, how many questions are ready —
 * and the next useful step. A button that does its job closes the panel; its toast says what happened, and the call's ⋯
 * opens on where it has got to.
 */
export function CallAssistantPanel({
  projectId,
  candidateId,
  meeting,
  recording,
  mayRecord,
  mayStartNow,
  offersQuestions,
  firstName,
  onPrepareQuestions,
  onOpenCopilot,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  meeting: Meeting;
  /** The call's latest bot, where one was ever sent. */
  recording: NotetakerRecording | null;
  /** A bot may be sent to this call by the caller, and none is on its way. */
  mayRecord: boolean;
  /** The call's day has begun in the caller's time zone, so the bot may join at once. */
  mayStartNow: boolean;
  /** The call is still to come, so questions can be prepared for it. */
  offersQuestions: boolean;
  firstName: string;
  onPrepareQuestions: () => void;
  onOpenCopilot: (recordingId: string) => void;
  onClose: () => void;
}) {
  const actions = useNotetakerActions(projectId, candidateId, firstName);
  const questionsReady = useQuestionsReady(projectId, candidateId, meeting.id, offersQuestions);
  const [wantsCopilot, setWantsCopilot] = useState(true);
  const [joinAtStart, setJoinAtStart] = useState(true);
  const [confirming, setConfirming] = useState<"end" | "remove" | null>(null);
  const bot = isNotetakerLive(recording) ? recording : null;
  const isRecording = bot?.status === "RECORDING";
  const now = Date.now();
  const mayJoinAtStart = new Date(meeting.startsAt).getTime() - now > JOIN_NOW_LEAD_MS;
  const isBusy =
    actions.send.isPending || actions.joinNow.isPending || actions.remove.isPending || actions.switchLive.isPending;

  const send = (join: NotetakerJoin) =>
    actions.send.mutate({ meetingId: meeting.id, join, liveCopilot: wantsCopilot }, { onSuccess: onClose });

  return (
    <Modal
      open
      onClose={onClose}
      title="Notetaker & questions"
      subtitle={`${meeting.title ?? "Untitled"} · ${meetingWhenOf(meeting.startsAt, meeting.endsAt)}`}
      closeButton
      className="md:w-[460px]"
    >
      <div className="pb-1">
        {offersQuestions && (
          <PanelSection
            icon={ICONS.checklist}
            title="Prepare questions"
            state={questionsReady === null ? null : `${questionsReady} ready`}
          >
            {questionsReady === null && (
              <p className="text-note text-u-text3">
                Questions for this call from the brief, and for what {firstName}&apos;s record is missing.
              </p>
            )}
            <div>
              <Button size="xs" variant="secondary" onClick={onPrepareQuestions}>
                {questionsReady === null ? "Prepare questions" : "Open questions"}
              </Button>
            </div>
          </PanelSection>
        )}

        {(bot || mayRecord) && (
          <PanelSection icon={ICONS.mic} title="Notetaker" state={stateOf(bot)}>
            {!bot && (
              <>
                <p className="text-note text-u-text3">
                  Records the call and files the notes on {firstName}&apos;s record. Ask {firstName} first.
                </p>
                {recording?.status === "FAILED" && (
                  <p className="text-note text-u-text2">
                    The last attempt didn&apos;t work: {failureWordsOf(recording.failureReason)}. You can send it again.
                  </p>
                )}
                <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
                  <CopilotSwitch checked={wantsCopilot} disabled={isBusy} onChange={setWantsCopilot} />
                  {mayJoinAtStart && (
                    <SwitchRow
                      checked={joinAtStart}
                      disabled={isBusy}
                      onChange={setJoinAtStart}
                      label={`Join at start · ${timeOfDayOf(meeting.startsAt)}`}
                    />
                  )}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {mayJoinAtStart && joinAtStart && (
                    <Button size="xs" disabled={isBusy} onClick={() => send("AT_START")}>
                      Schedule Notetaker
                    </Button>
                  )}
                  {mayStartNow && (
                    <Button
                      size="xs"
                      variant={mayJoinAtStart && joinAtStart ? "secondary" : "primary"}
                      disabled={isBusy}
                      onClick={() => send("NOW")}
                    >
                      Send Notetaker now
                    </Button>
                  )}
                  <OpenMeetingLink meeting={meeting} />
                </div>
                {!mayStartNow && mayJoinAtStart && <Hint>Send now opens on the day of the call.</Hint>}
              </>
            )}

            {bot?.status === "SCHEDULED" && bot.joinAt !== null && (
              <>
                <p className="text-note text-u-text3">
                  Joins a minute before the call starts, at {timeOfDayOf(bot.joinAt)}.
                </p>
                <CopilotSwitch
                  checked={bot.liveCopilot}
                  disabled={!bot.mayChangeCopilot || isBusy}
                  onChange={(enabled) => actions.switchLive.mutate({ recordingId: bot.id, enabled })}
                  note={bot.mayChangeCopilot ? null : "Only whoever sent the Notetaker can change it."}
                />
                <div className="flex flex-wrap items-center gap-2">
                  {mayStartNow && new Date(bot.joinAt).getTime() - now > JOIN_NOW_LEAD_MS && (
                    <Button
                      size="xs"
                      disabled={isBusy}
                      onClick={() => actions.joinNow.mutate(bot.id, { onSuccess: onClose })}
                    >
                      Send now instead
                    </Button>
                  )}
                  <QuietButton disabled={isBusy} onClick={() => setConfirming("remove")}>
                    Remove
                  </QuietButton>
                </div>
                {!mayStartNow && <Hint>Send now opens on the day of the call.</Hint>}
              </>
            )}

            {((bot?.status === "SCHEDULED" && bot.joinAt === null) || bot?.status === "JOINING") && (
              <>
                <p className="text-note text-u-text3">On its way into the call. Let it in if it waits in the lobby.</p>
                <CopilotLine recording={bot} onOpenCopilot={onOpenCopilot} />
                <div>
                  <QuietButton disabled={isBusy} onClick={() => setConfirming("remove")}>
                    Remove
                  </QuietButton>
                </div>
              </>
            )}

            {bot?.status === "RECORDING" &&
              (bot.endRequested ? (
                <p className="text-note text-u-text3">Leaving the call. The notes follow in a minute or two.</p>
              ) : (
                <>
                  <div className="flex flex-wrap gap-2">
                    <Button size="xs" disabled={actions.end.isPending} onClick={() => setConfirming("end")}>
                      End &amp; file notes
                    </Button>
                    {bot.liveCopilot && (
                      <Button size="xs" variant="secondary" onClick={() => onOpenCopilot(bot.id)}>
                        Open copilot
                        <Icon d={ICONS.externalLink} size={12} />
                      </Button>
                    )}
                  </div>
                  <p className="text-meta text-u-text3">
                    <button
                      type="button"
                      disabled={isBusy}
                      onClick={() => setConfirming("remove")}
                      className="font-medium text-u-offlimits hover:underline disabled:opacity-60"
                    >
                      Remove and discard
                    </button>{" "}
                    · leaves the call and files nothing
                  </p>
                </>
              ))}
          </PanelSection>
        )}
      </div>

      {bot && (
        <>
          <ConfirmDialog
            open={confirming === "end"}
            tone="primary"
            title="End the recording and file the notes?"
            confirmLabel="End & file notes"
            cancelLabel="Keep recording"
            pending={actions.end.isPending}
            onConfirm={() =>
              actions.end.mutate(bot.id, {
                onSuccess: onClose,
                onSettled: () => setConfirming(null),
              })
            }
            onClose={() => setConfirming(null)}
          >
            <p>The Notetaker leaves the call now and writes up what it heard. It can&apos;t rejoin this call.</p>
          </ConfirmDialog>
          <ConfirmDialog
            open={confirming === "remove"}
            title={isRecording ? "Remove and discard?" : "Remove the Notetaker?"}
            confirmLabel={isRecording ? "Remove and discard" : "Remove notetaker"}
            cancelLabel={isRecording ? "Keep recording" : "Keep it"}
            pending={actions.remove.isPending}
            onConfirm={() =>
              actions.remove.mutate(bot.id, {
                onSuccess: onClose,
                onSettled: () => setConfirming(null),
              })
            }
            onClose={() => setConfirming(null)}
          >
            <p>
              {isRecording
                ? "The Notetaker leaves the call, and nothing it recorded is kept or filed. To keep the notes, use End & file notes."
                : "It leaves the call, or never joins it, and no notes are filed for this call. You can send it again while the call is still on."}
            </p>
          </ConfirmDialog>
        </>
      )}
    </Modal>
  );
}

/** What the Notetaker is set to, in a few words beside its section's title. */
function stateOf(bot: NotetakerRecording | null): ReactNode {
  if (!bot) return "Off";
  if (bot.status === "SCHEDULED" && bot.joinAt !== null) return "Joins at start";
  if (bot.status === "RECORDING") return bot.endRequested ? "Ending" : "Recording";
  return "Joining";
}

function PanelSection({
  icon,
  title,
  state,
  children,
}: {
  icon: string;
  title: string;
  state: ReactNode;
  children: ReactNode;
}) {
  return (
    <section aria-label={title} className="border-t border-u-border py-4 first:border-t-0 first:pt-1">
      <div className="flex items-center gap-2">
        <Icon d={icon} size={14} className="flex-none text-u-text3" />
        <h3 className="text-body font-semibold text-u-text">{title}</h3>
        {state && <span className="ms-auto font-mono text-meta text-u-accent">{state}</span>}
      </div>
      <div className="ms-[22px] mt-1.5 flex flex-col gap-3">{children}</div>
    </section>
  );
}

function SwitchRow({
  checked,
  disabled,
  onChange,
  label,
}: {
  checked: boolean;
  disabled: boolean;
  onChange: (next: boolean) => void;
  label: string;
}) {
  return (
    <div className="flex items-center gap-2.5">
      <Toggle checked={checked} onChange={onChange} label={label} disabled={disabled} />
      <span className="text-note text-u-text">{label}</span>
    </div>
  );
}

/** The call's own link in a new tab: sending the Notetaker never opens it, so a call already running is not joined twice. */
function OpenMeetingLink({ meeting }: { meeting: Meeting }) {
  if (!meeting.joinUrl) return null;
  return (
    <a
      href={meeting.joinUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 rounded-u-control px-1.5 py-1 text-note font-medium text-u-text3 hover:text-u-text"
    >
      <Icon d={ICONS.video} size={12} />
      Open {meeting.videoProvider ?? "meeting"}
    </a>
  );
}

function CopilotSwitch({
  checked,
  disabled,
  onChange,
  note = null,
}: {
  checked: boolean;
  disabled: boolean;
  onChange: (next: boolean) => void;
  /** Said in place of what the copilot does, where the switch is not the caller's. */
  note?: string | null;
}) {
  return (
    <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1">
      <Toggle checked={checked} onChange={onChange} label="Live copilot" disabled={disabled} />
      <span className="text-note text-u-text">Live copilot</span>
      <span className="text-meta text-u-text3">{note ?? "Nudges only you during the call."}</span>
    </div>
  );
}

/** A bot on its way in carries the copilot it was sent with: chosen before it was sent, so only said here. */
function CopilotLine({
  recording,
  onOpenCopilot,
}: {
  recording: NotetakerRecording;
  onOpenCopilot: (recordingId: string) => void;
}) {
  if (!recording.liveCopilot) {
    return <p className="text-meta text-u-text3">Live copilot is off for this call. It is chosen before it is sent.</p>;
  }
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Button size="xs" variant="secondary" onClick={() => onOpenCopilot(recording.id)}>
        Open copilot
        <Icon d={ICONS.externalLink} size={12} />
      </Button>
    </div>
  );
}

function QuietButton({
  disabled,
  onClick,
  children,
}: {
  disabled: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="rounded-u-control px-1.5 py-1 text-note font-medium text-u-text3 hover:text-u-text disabled:opacity-60"
    >
      {children}
    </button>
  );
}

function Hint({ children }: { children: ReactNode }) {
  return <p className="text-meta text-u-text3">{children}</p>;
}
