import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { useNavigate, useOutletContext, useParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Modal, Skeleton, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as sequenceApi from "../api/sequenceApi";
import type { RecipientTokens, Sequence, SequenceSchedule, SequenceStep, Weekday } from "../api/sequenceApi";
import {
  clockOf,
  DEFAULT_SCHEDULE,
  HALF_HOURS,
  MONDAY_TO_FRIDAY,
  SUNDAY_TO_THURSDAY,
  WEEKDAYS,
  zoneCityOf,
} from "../lib/sendSchedule";
import { BOOKING_LINK_PLACEHOLDER, firstNameOf, renderParts, tokenOptions } from "../lib/sequenceTokens";
import { useMailbox } from "../lib/useMailbox";
import { SequenceStatePill } from "../components/SequenceStatePill";

const NEW_SEQUENCE = "new";
const MAX_STEPS = 3;
const DELAY_CHOICES = [2, 3, 4, 5, 7];
const STEP_LABELS = ["First email", "Follow-up", "Last follow-up"];
const PREVIEW_OPENER = "Their opener is drafted for each person when you add them, and you review it then.";

const FIRST_STEP_BODY = `Hi {{firstName}},

{{opener}}

I'm leading a confidential search for a {{positionTitle}}. Given your time as {{currentTitle}} at {{currentCompany}}, I'd value twenty minutes of your view, whether or not the role is right for you.

Would a short call next week work?

{{senderFirstName}}`;

const DEFAULT_STEPS: SequenceStep[] = [
  {
    delayWorkingDays: 0,
    subject: "Confidential: {{positionTitle}}",
    body: FIRST_STEP_BODY,
  },
  {
    delayWorkingDays: 3,
    subject: null,
    body: "Hi {{firstName}},\n\nFollowing up on my note below, in case it slipped past. Happy to work around your diary.\n\n{{senderFirstName}}",
  },
  {
    delayWorkingDays: 5,
    subject: null,
    body: "Hi {{firstName}},\n\nI'll leave it here for now. If the timing is ever better, I'd be glad to hear from you.\n\n{{senderFirstName}}",
  },
];

/** `Outreach.dc.html?page=sequence`: the steps on the left, a live preview as a mapped person on the right. */
export function SequenceEditorPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { sequenceId = NEW_SEQUENCE } = useParams();
  const sequence = useQuery({
    queryKey: sequenceApi.SEQUENCE_KEY(project.id, sequenceId),
    queryFn: ({ signal }) => sequenceApi.getSequence(project.id, sequenceId, signal),
    enabled: sequenceId !== NEW_SEQUENCE,
  });

  if (sequenceId !== NEW_SEQUENCE && !sequence.data) {
    return (
      <div className="mx-auto max-w-[1440px] px-4 pb-20 pt-[22px] md:px-7">
        {sequence.isError ? (
          <p role="alert" className="text-[13px] text-u-text3">
            {messageFor(sequence.error)}
          </p>
        ) : (
          <Skeleton className="h-[420px] w-full" />
        )}
      </div>
    );
  }
  return (
    <SequenceEditor
      key={sequenceId}
      projectId={project.id}
      positionTitle={project.positionTitle}
      saved={sequence.data ?? null}
    />
  );
}

function SequenceEditor({
  projectId,
  positionTitle,
  saved,
}: {
  projectId: string;
  positionTitle: string;
  saved: Sequence | null;
}) {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [name, setName] = useState(saved?.name ?? "New sequence");
  const [steps, setSteps] = useState<SequenceStep[]>(saved?.steps ?? DEFAULT_STEPS);
  const [schedule, setSchedule] = useState<SequenceSchedule>(() =>
    saved?.schedule
      ? { ...saved.schedule, windowStart: clockOf(saved.schedule.windowStart), windowEnd: clockOf(saved.schedule.windowEnd) }
      : DEFAULT_SCHEDULE,
  );
  const [selectedStep, setSelectedStep] = useState(0);
  const bodyRefs = useRef<(HTMLTextAreaElement | null)[]>([]);
  const subjectRef = useRef<HTMLInputElement | null>(null);
  const [editingField, setEditingField] = useState<"subject" | "body">("body");
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
  const mailbox = useMailbox();
  const backToOverview = () => navigate(`/projects/${projectId}/outreach`);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: name.trim(),
        steps: steps.map((step, index) => ({
          ...step,
          sendTime: index === 0 || !step.sendTime ? null : clockOf(step.sendTime),
        })),
        schedule,
      };
      return saved
        ? sequenceApi.updateSequence(projectId, saved.id, body)
        : sequenceApi.createSequence(projectId, body);
    },
    onSuccess: (written) => {
      void queryClient.invalidateQueries({
        queryKey: sequenceApi.SEQUENCES_KEY(projectId),
      });
      toast(
        saved && saved.enrolledCount > 0
          ? "Sequence saved. People already in it get the new wording from their next step."
          : "Sequence saved.",
      );
      if (!saved)
        navigate(`/projects/${projectId}/outreach/sequences/${written.id}`, {
          replace: true,
        });
    },
    onError: (error) => toast(messageFor(error)),
  });

  const remove = useMutation({
    mutationFn: () => sequenceApi.deleteSequence(projectId, saved!.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: sequenceApi.SEQUENCES_KEY(projectId) });
      toast("Sequence deleted.");
      backToOverview();
    },
    onError: (error) => {
      setIsConfirmingDelete(false);
      toast(messageFor(error));
    },
  });

  const changeStep = (index: number, change: Partial<SequenceStep>) =>
    setSteps((current) => current.map((step, at) => (at === index ? { ...step, ...change } : step)));

  const removeStep = (index: number) => {
    setSteps((current) => current.filter((_, at) => at !== index));
    setSelectedStep(0);
  };

  const addStep = () => {
    setSteps((current) => (current.length >= MAX_STEPS ? current : [...current, { ...DEFAULT_STEPS[current.length] }]));
    setSelectedStep(steps.length);
  };

  /** Into whichever field the caret was last in: the first email's subject, or the selected step's body. */
  const insertToken = (token: string) => {
    const intoSubject = editingField === "subject" && selectedStep === 0;
    const field = intoSubject ? subjectRef.current : bodyRefs.current[selectedStep];
    const text = (intoSubject ? steps[0].subject : steps[selectedStep].body) ?? "";
    const start = field?.selectionStart ?? text.length;
    const end = field?.selectionEnd ?? text.length;
    const inserted = text.slice(0, start) + token + text.slice(end);
    changeStep(selectedStep, intoSubject ? { subject: inserted } : { body: inserted });
    requestAnimationFrame(() => {
      field?.focus();
      field?.setSelectionRange(start + token.length, start + token.length);
    });
  };

  const isLive = (saved?.enrolledCount ?? 0) > 0;
  const isScheduleUsable = schedule.days.length > 0 && schedule.windowStart < schedule.windowEnd;
  const stepTimeOutsideWindow = steps.some(
    (step, index) =>
      index > 0 &&
      step.sendTime &&
      (clockOf(step.sendTime) < schedule.windowStart || clockOf(step.sendTime) >= schedule.windowEnd),
  );

  return (
    <div className="mx-auto max-w-[1440px] px-4 pb-20 pt-[22px] md:px-7">
      <div className="mb-[18px] flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={backToOverview}
          className="inline-flex items-center gap-1.5 rounded-[6px] px-2 py-1.5 font-mono text-[13px] font-medium text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.back} size={14} />
          Outreach
        </button>
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-label="Sequence name"
          maxLength={120}
          className="min-w-0 flex-1 rounded-[7px] border border-transparent bg-transparent px-2 py-1.5 text-[18px] font-semibold text-u-text outline-none focus:border-u-border focus:bg-u-raised md:max-w-[420px]"
        />
        <SequenceStatePill isLive={isLive} />
        {saved && (
          <Button
            variant="ghost"
            className="ms-auto px-2.5 py-[7px] text-[13px]"
            onClick={() => setIsConfirmingDelete(true)}
          >
            Delete sequence
          </Button>
        )}
        <Button
          className={cn("px-3.5 py-[7px] text-[13px] font-semibold", !saved && "ms-auto")}
          loading={save.isPending}
          disabled={!name.trim() || !isScheduleUsable || stepTimeOutsideWindow}
          onClick={() => save.mutate()}
        >
          Save sequence
        </Button>
      </div>

      <div className="grid items-start gap-[22px] lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-1.5 rounded-[8px] border border-u-border bg-u-raised px-2.5 py-2">
            <span className="me-1 font-mono text-[11px] font-medium text-u-text3">Insert</span>
            {tokenOptions(mailbox.data?.bookingLinkOffered === true).map((option) => (
              <button
                key={option.token}
                type="button"
                title={option.tip}
                onClick={() => insertToken(option.token)}
                className={cn(
                  "rounded-full border px-2 py-[3px] font-mono text-[11.5px]",
                  option.isAi
                    ? "border-transparent bg-u-inferred-tint text-u-inferred"
                    : "border-u-border bg-u-surface text-u-text2 hover:text-u-text",
                )}
              >
                {option.isAi ? "✦ opener" : option.isLink ? "▦ booking link" : option.token}
              </button>
            ))}
          </div>

          {steps.map((step, index) => (
            <div
              key={index}
              onClick={() => setSelectedStep(index)}
              className={cn(
                "rounded-[10px] border bg-u-surface p-3.5",
                selectedStep === index ? "border-u-accent" : "border-u-border",
              )}
            >
              <div className="mb-2.5 flex items-center gap-2.5">
                <span className="grid size-[22px] place-items-center rounded-full bg-u-accent-tint font-mono text-[11px] font-bold text-u-accent">
                  {index + 1}
                </span>
                <span className="text-[13px] font-semibold">{STEP_LABELS[index]}</span>
                <span className="font-mono text-[12px] text-u-text3">
                  {index === 0
                    ? "sent when you start"
                    : `+${step.delayWorkingDays} sending ${step.delayWorkingDays === 1 ? "day" : "days"}${
                        step.sendTime ? ` at ${clockOf(step.sendTime)}` : ""
                      }`}
                </span>
                {index > 0 && (
                  <button
                    type="button"
                    title="Remove this step"
                    aria-label="Remove this step"
                    onClick={(event) => {
                      event.stopPropagation();
                      removeStep(index);
                    }}
                    className="ms-auto rounded-[6px] p-[5px] text-u-text3 hover:bg-u-raised hover:text-u-offlimits"
                  >
                    <Icon d={ICONS.trash} size={13} />
                  </button>
                )}
              </div>
              {index > 0 && (
                <div className="mb-2 flex flex-wrap items-center gap-2 font-mono text-[12px] text-u-text2">
                  Send
                  <select
                    value={step.delayWorkingDays}
                    aria-label="Days to wait"
                    onChange={(event) =>
                      changeStep(index, {
                        delayWorkingDays: Number(event.target.value),
                      })
                    }
                    className="rounded-[6px] border border-u-border bg-u-raised px-1.5 py-1 font-mono text-[12px] text-u-text"
                  >
                    {delayChoicesWith(step.delayWorkingDays).map((days) => (
                      <option key={days} value={days}>
                        {days}
                      </option>
                    ))}
                  </select>
                  sending days after the last step, at
                  <select
                    value={step.sendTime ? clockOf(step.sendTime) : ""}
                    aria-label="Time of day"
                    onChange={(event) => changeStep(index, { sendTime: event.target.value || null })}
                    className={cn(
                      "rounded-[6px] border bg-u-raised px-1.5 py-1 font-mono text-[12px] text-u-text",
                      step.sendTime &&
                        (clockOf(step.sendTime) < schedule.windowStart || clockOf(step.sendTime) >= schedule.windowEnd)
                        ? "border-u-offlimits"
                        : "border-u-border",
                    )}
                  >
                    <option value="">the same time</option>
                    {timeChoicesWith(
                      HALF_HOURS.filter((time) => time >= schedule.windowStart && time < schedule.windowEnd),
                      step.sendTime ? clockOf(step.sendTime) : null,
                    ).map((time) => (
                      <option key={time} value={time}>
                        {time}
                      </option>
                    ))}
                  </select>
                  if they haven't replied, as a reply in the same thread
                </div>
              )}
              {index === 0 && (
                <input
                  ref={subjectRef}
                  value={step.subject ?? ""}
                  aria-label="Subject"
                  onFocus={() => {
                    setSelectedStep(0);
                    setEditingField("subject");
                  }}
                  maxLength={200}
                  onChange={(event) => changeStep(index, { subject: event.target.value })}
                  className="mb-2 w-full rounded-[6px] border border-u-border bg-u-raised px-2.5 py-2 text-[13px] font-medium text-u-text outline-none"
                />
              )}
              <textarea
                ref={(element) => {
                  bodyRefs.current[index] = element;
                }}
                value={step.body}
                aria-label="Email body"
                rows={index === 0 ? 11 : 6}
                maxLength={5000}
                onFocus={() => {
                  setSelectedStep(index);
                  setEditingField("body");
                }}
                onChange={(event) => changeStep(index, { body: event.target.value })}
                className="w-full resize-y rounded-[6px] border border-u-border bg-u-raised p-2.5 text-[13px]/[1.55] text-u-text outline-none"
              />
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-2.5 font-mono text-[12px] text-u-text3">
            <button
              type="button"
              disabled={steps.length >= MAX_STEPS}
              onClick={addStep}
              className="rounded-[6px] border border-dashed border-u-border px-[11px] py-1.5 text-[12px] font-medium text-u-text3 enabled:hover:text-u-text disabled:cursor-not-allowed"
            >
              ＋ Add step
            </button>
            Three emails catch nearly every reply a sequence will get.
          </div>

          <SendingScheduleCard
            schedule={schedule}
            timeZone={mailbox.data?.connection?.timeZone ?? null}
            hasStepOutsideWindow={Boolean(stepTimeOutsideWindow)}
            onChange={setSchedule}
          />
          <SequenceRules />
        </div>

        <SequencePreview
          projectId={projectId}
          positionTitle={positionTitle}
          steps={steps}
          selectedStep={selectedStep}
          onSelectStep={setSelectedStep}
        />
      </div>
      <Modal
        open={isConfirmingDelete}
        onClose={() => setIsConfirmingDelete(false)}
        title="Delete this sequence?"
        footer={
          <>
            <Button variant="secondary" className="px-3.5 py-2 text-[13px]" onClick={() => setIsConfirmingDelete(false)}>
              Cancel
            </Button>
            <Button className="px-3.5 py-2 text-[13px] font-semibold" loading={remove.isPending} onClick={() => remove.mutate()}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-[13px] text-u-text2">
          {saved?.name} goes for good. A sequence anyone has been put on stays, since it is the record of their
          approach.
        </p>
      </Modal>
    </div>
  );
}

/** `Outreach.dc.html?page=sequence`'s Sending schedule: the days and hours its follow-ups may go. */
function SendingScheduleCard({
  schedule,
  timeZone,
  hasStepOutsideWindow,
  onChange,
}: {
  schedule: SequenceSchedule;
  timeZone: string | null;
  hasStepOutsideWindow: boolean;
  onChange: (schedule: SequenceSchedule) => void;
}) {
  const toggleDay = (day: Weekday) =>
    onChange({
      ...schedule,
      days: schedule.days.includes(day) ? schedule.days.filter((held) => held !== day) : [...schedule.days, day],
    });
  const isSameDays = (days: Weekday[]) =>
    days.length === schedule.days.length && days.every((day) => schedule.days.includes(day));
  const timeSelectClass =
    "rounded-[6px] border border-u-border bg-u-raised px-1.5 py-1 font-mono text-[12px] text-u-text";

  return (
    <div className="rounded-[10px] border border-u-border px-4 py-3.5">
      <div className="mb-1 text-[13px] font-semibold">Sending schedule</div>
      <p className="mb-2.5 text-[12px]/[1.5] text-u-text3">
        Emails go only on these days and between these hours, in your mailbox's time zone
        {timeZone ? ` (${zoneCityOf(timeZone)})` : ""}. The days in the delays above are these days. A first email you
        start Now, or at a time you pick, goes when you said.
      </p>
      <div role="group" aria-label="Sending days" className="mb-2.5 flex flex-wrap items-center gap-1.5">
        {WEEKDAYS.map(({ day, label }) => {
          const isOn = schedule.days.includes(day);
          return (
            <button
              key={day}
              type="button"
              aria-pressed={isOn}
              onClick={() => toggleDay(day)}
              className={cn(
                "rounded-full border px-2.5 py-1 font-mono text-[12px] font-medium",
                isOn ? "border-u-accent bg-u-accent-tint text-u-text" : "border-u-border text-u-text3 hover:text-u-text",
              )}
            >
              {label}
            </button>
          );
        })}
        <span className="ms-1.5 flex items-center gap-1.5 font-mono text-[11.5px] text-u-text3">
          {(
            [
              ["Mon–Fri", MONDAY_TO_FRIDAY],
              ["Sun–Thu", SUNDAY_TO_THURSDAY],
            ] as const
          ).map(([label, days]) => (
            <button
              key={label}
              type="button"
              disabled={isSameDays(days)}
              onClick={() => onChange({ ...schedule, days: [...days] })}
              className="font-medium text-u-accent disabled:text-u-text3"
            >
              {label}
            </button>
          ))}
        </span>
      </div>
      <div className="flex flex-wrap items-center gap-2 font-mono text-[12px] text-u-text2">
        Between
        <select
          value={schedule.windowStart}
          aria-label="Sending starts"
          onChange={(event) => onChange({ ...schedule, windowStart: event.target.value })}
          className={timeSelectClass}
        >
          {timeChoicesWith(HALF_HOURS, schedule.windowStart).map((time) => (
            <option key={time} value={time}>
              {time}
            </option>
          ))}
        </select>
        and
        <select
          value={schedule.windowEnd}
          aria-label="Sending stops"
          onChange={(event) => onChange({ ...schedule, windowEnd: event.target.value })}
          className={timeSelectClass}
        >
          {timeChoicesWith([...HALF_HOURS.slice(1), "23:59"], schedule.windowEnd).map((time) => (
            <option key={time} value={time}>
              {time}
            </option>
          ))}
        </select>
      </div>
      {schedule.days.length === 0 && (
        <p role="alert" className="mt-2 text-[12px] text-u-offlimits">
          Choose at least one day to send on.
        </p>
      )}
      {schedule.windowStart >= schedule.windowEnd && (
        <p role="alert" className="mt-2 text-[12px] text-u-offlimits">
          Sending has to stop after it starts.
        </p>
      )}
      {hasStepOutsideWindow && (
        <p role="alert" className="mt-2 text-[12px] text-u-offlimits">
          A follow-up's time is outside these hours. Change its time or the hours.
        </p>
      )}
    </div>
  );
}

function SequenceRules() {
  return (
    <div className="rounded-[10px] border border-u-border px-4 py-3.5">
      <div className="mb-2 text-[13px] font-semibold">When it stops</div>
      <div className="grid grid-cols-[100px_1fr] gap-x-3 gap-y-1.5 text-[12.5px]/[1.5] text-u-text2 md:grid-cols-[120px_1fr]">
        <span className="text-u-text3">Daily cap</span>
        <span>Each mailbox sends up to its daily cap. Anything over waits for the next window.</span>
        <span className="text-u-text3">Stops when</span>
        <span>
          They reply, they book a call through your booking link (they also move to Engaged), an email bounces, they
          are marked do not contact, they leave this position, or their status becomes Not interested, Off-limits or
          Out of scope.
        </span>
      </div>
    </div>
  );
}

function SequencePreview({
  projectId,
  positionTitle,
  steps,
  selectedStep,
  onSelectStep,
}: {
  projectId: string;
  positionTitle: string;
  steps: SequenceStep[];
  selectedStep: number;
  onSelectStep: (index: number) => void;
}) {
  const { user } = useAuth();
  const mailbox = useMailbox();
  const vocabulary = useWorkspaceVocabulary();
  const people = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(projectId, {}),
    queryFn: ({ signal }) => candidatesApi.getCandidates(projectId, {}, signal),
  });
  const [previewId, setPreviewId] = useState<string | null>(null);
  const candidates = people.data?.candidates ?? [];
  const person = candidates.find((candidate) => candidate.id === previewId) ?? candidates[0] ?? null;
  const step = steps[Math.min(selectedStep, steps.length - 1)];
  const tokens = tokensOf(person, positionTitle, user?.fullName ?? null,
    mailbox.data?.connection?.bookingLink ?? BOOKING_LINK_PLACEHOLDER);
  const firstSubject = steps[0]?.subject ?? "";
  const subjectParts = renderParts(firstSubject, tokens, null);
  const subject = subjectParts.map((part) => part.text).join("");
  const sender = mailbox.data?.connection?.address;

  return (
    <div className="rounded-[10px] border border-u-border bg-u-surface lg:sticky lg:top-0">
      <div className="flex flex-wrap items-center gap-2.5 border-b border-u-border px-3.5 py-2.5">
        <span className="text-[12px] font-semibold">Preview as</span>
        <select
          value={person?.id ?? ""}
          aria-label="Preview as"
          onChange={(event) => setPreviewId(event.target.value)}
          disabled={candidates.length === 0}
          className="min-w-0 max-w-[240px] rounded-[6px] border border-u-border bg-u-raised px-2 py-1 font-mono text-[12px] text-u-text"
        >
          {candidates.length === 0 && <option value="">Nobody mapped yet</option>}
          {candidates.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.fullName}
              {candidate.companyName ? ` · ${candidate.companyName}` : ""}
            </option>
          ))}
        </select>
        <div
          role="tablist"
          aria-label="Step"
          className="ms-auto inline-flex rounded-[6px] border border-u-border p-0.5"
        >
          {steps.map((_, index) => (
            <button
              key={index}
              type="button"
              role="tab"
              aria-selected={selectedStep === index}
              onClick={() => onSelectStep(index)}
              className={cn(
                "rounded-[4px] px-2 py-[3px] font-mono text-[11.5px]",
                selectedStep === index ? "bg-u-raised text-u-text" : "text-u-text3",
              )}
            >
              Step {index + 1}
            </button>
          ))}
        </div>
      </div>
      <div className="px-[18px] py-4">
        <div className="grid grid-cols-[56px_1fr] gap-x-2.5 gap-y-1 border-b border-u-border pb-3 font-mono text-[12.5px] text-u-text2">
          <span className="text-u-text3">From</span>
          <span className="truncate">
            {user?.fullName ?? "You"}
            {sender ? ` <${sender}>` : ""}
          </span>
          <span className="text-u-text3">To</span>
          <span className="truncate">{person?.contacts.emails[0]?.address ?? "—"}</span>
          <span className="text-u-text3">Subject</span>
          <span className="font-medium text-u-text">{selectedStep > 0 ? `Re: ${subject}` : subject}</span>
        </div>
        <div className="whitespace-pre-wrap pt-3.5 text-[13.5px]/[1.6] text-u-text">
          {renderParts(step?.body ?? "", tokens, PREVIEW_OPENER).map((part, index) =>
            part.isLink ? (
              <span key={index} className="text-u-accent underline">
                {part.text}
              </span>
            ) : part.isOpener ? (
              <span
                key={index}
                className="rounded-[4px] bg-u-inferred-tint px-[3px] py-px shadow-[inset_0_-1px_0_var(--color-u-inferred)]"
              >
                {part.text}
              </span>
            ) : (
              <span key={index}>{part.text}</span>
            ),
          )}
        </div>
        {selectedStep > 0 && (
          <div className="mt-3.5 border-s-2 border-u-border ps-2.5 text-[12px]/[1.5] text-u-text3">
            Your earlier email to {tokens.firstName ?? "them"} is quoted below, as in any reply.
          </div>
        )}
      </div>
      <div className="flex items-start gap-2 rounded-b-[10px] border-t border-u-border bg-u-inferred-tint px-3.5 py-2.5 text-[12px]/[1.5] text-u-text2">
        <span className="mt-0.5 flex-none text-u-inferred">
          <Icon d={ICONS.sparkle} size={14} />
        </span>
        <span>
          The highlighted opener is drafted by AI for each person from their profile and this brief's role. It never
          names the {vocabulary.unitLower} or any figure. You read and can rewrite every opener before the first email
          goes.
        </span>
      </div>
    </div>
  );
}

function tokensOf(
  person: Candidate | null,
  positionTitle: string,
  senderName: string | null,
  bookingLink: string,
): RecipientTokens {
  return {
    bookingLink,
    firstName: firstNameOf(person?.fullName),
    currentTitle: person?.title ?? null,
    currentCompany: person?.companyName ?? null,
    positionTitle,
    location: person?.locationCity ?? person?.locationCountry ?? null,
    senderFirstName: firstNameOf(senderName),
  };
}

/** A saved time the picker does not list (the server takes any minute) is still shown as chosen. */
function timeChoicesWith(choices: readonly string[], current: string | null): string[] {
  return current === null || choices.includes(current) ? [...choices] : [...choices, current].sort();
}

/** A saved delay the picker does not list (the server takes 1–30) is still shown as chosen. */
function delayChoicesWith(current: number): number[] {
  return DELAY_CHOICES.includes(current) ? DELAY_CHOICES : [...DELAY_CHOICES, current].sort((a, b) => a - b);
}
