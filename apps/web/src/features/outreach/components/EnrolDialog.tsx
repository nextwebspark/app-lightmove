import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button, ChoiceCardGroup, DateInput, Modal, Skeleton, useToast } from "../../../components/ui";
import type { ChoiceCardOption } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { MAILBOX_KEY } from "../api/mailboxApi";
import * as sequenceApi from "../api/sequenceApi";
import type {
  EnrollmentCandidate,
  EnrollmentScope,
  OutreachSkipReason,
  RecipientTokens,
  Sequence,
  StartedSequence,
  StartMode,
} from "../api/sequenceApi";
import {
  clockOf,
  dayLabelOf,
  firstSendOf,
  followUpDatesOf,
  HALF_HOURS,
  instantOf,
  isOutsideSchedule,
  nextOpeningOf,
  scheduleLabelOf,
  wallClockOf,
  whenLabelOf,
  zoneCityOf,
} from "../lib/sendSchedule";
import { BOOKING_LINK_PLACEHOLDER, render, renderParts } from "../lib/sequenceTokens";
import { useMailbox } from "../lib/useMailbox";

type EnrolStep = "choose" | "review" | "start";

const STEPS: { id: EnrolStep; label: string }[] = [
  { id: "choose", label: "Choose" },
  { id: "review", label: "Review" },
  { id: "start", label: "Start" },
];

/** One person's first email as the review leaves it. Kept per person, so an edit reaches nobody else. */
interface ReviewedEmail {
  toAddress: string;
  opener: string;
  openerEdited: boolean;
  isDrafting: boolean;
  draftFailed: boolean;
}

/** `StartSequenceRequest`'s horizon for a chosen start. */
const START_HORIZON_DAYS = 60;

/** Until the mailbox read lands; the server's own default. */
const FALLBACK_ZONE = "Asia/Dubai";

/** When the first emails go, as the Start step leaves it: a mode, and with `AT` a day and time in the sender's zone. */
interface StartChoice {
  mode: StartMode;
  date: string;
  time: string;
}

const SKIP_NOTES: Record<OutreachSkipReason, string> = {
  NO_EMAIL: "No email · Find email in the drawer",
  DO_NOT_CONTACT: "Do not contact",
  LEFT_THE_RUNNING: "Out of the running on this position",
  ALREADY_IN_SEQUENCE: "Already in a sequence",
};

const SKIP_SUMMARY: Record<OutreachSkipReason, string> = {
  NO_EMAIL: "no email",
  DO_NOT_CONTACT: "do not contact",
  LEFT_THE_RUNNING: "out of the running",
  ALREADY_IN_SEQUENCE: "already in a sequence",
};

/**
 * Add to sequence (`Outreach.dc.html?dialog=enrol`): choose the people, review each first email with its
 * AI opener, then start. Nothing is sent from here — Start schedules, and the dispatcher sends.
 */
export function EnrolDialog({
  projectId,
  scope,
  source,
  onClose,
}: {
  projectId: string;
  scope: EnrollmentScope;
  /** The Choose step's caption: where these people came from. */
  source: string;
  onClose: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const mailbox = useMailbox();
  const sequences = useQuery({
    queryKey: sequenceApi.SEQUENCES_KEY(projectId),
    queryFn: ({ signal }) => sequenceApi.getSequences(projectId, signal),
  });
  const people = useQuery({
    queryKey: ["outreach", projectId, "enrollment-candidates", scope],
    queryFn: ({ signal }) => sequenceApi.getEnrollmentCandidates(projectId, scope, signal),
    staleTime: 0,
  });

  const [step, setStep] = useState<EnrolStep>("choose");
  const [chosenSequenceId, setChosenSequenceId] = useState<string | null>(null);
  const [unticked, setUnticked] = useState<ReadonlySet<string>>(new Set());
  const [reviewed, setReviewed] = useState<Record<string, ReviewedEmail>>({});
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [startChoice, setStartChoice] = useState<StartChoice>({ mode: "NOW", date: "", time: "" });
  const isOpen = useRef(true);
  // Set in the body as well as cleared in the cleanup: StrictMode mounts, unmounts and mounts again,
  // and a ref only cleared would discard every draft that lands after that.
  useEffect(() => {
    isOpen.current = true;
    return () => {
      isOpen.current = false;
    };
  }, []);

  const sequence =
    sequences.data?.find((candidate) => candidate.id === chosenSequenceId) ?? sequences.data?.[0] ?? null;
  const everyone = people.data ?? [];
  const addable = everyone.filter((person) => person.skipReason === null);
  const skipped = everyone.filter((person) => person.skipReason !== null);
  const chosen = addable.filter((person) => !unticked.has(person.candidateId));
  const isOverTheCap = chosen.length > sequenceApi.MAX_PEOPLE_PER_START;
  const connection = mailbox.data?.connection ?? null;
  const canSend = connection?.status === "ACTIVE";
  const timeZone = connection?.timeZone ?? FALLBACK_ZONE;
  const chosenStart =
    startChoice.mode === "AT" && startChoice.date && startChoice.time
      ? instantOf(startChoice.date, startChoice.time, timeZone)
      : null;
  const startProblem = startProblemOf(startChoice.mode, chosenStart);

  /**
   * Drafts in presses of `OPENERS_PER_PRESS` and answers the ids that received an opener. Stops at the
   * first press that fails: the rest would fail alike (an exhausted budget), each with its own toast.
   * A batch draft never replaces an opener the consultant has typed; a Redraft (`replaceEdited`) does.
   */
  const draft = async (candidateIds: string[], replaceEdited = false): Promise<ReadonlySet<string>> => {
    const received = new Set<string>();
    setReviewed((current) =>
      withEach(current, candidateIds, (email) => ({ ...email, isDrafting: true, draftFailed: false })),
    );
    for (let at = 0; at < candidateIds.length; at += sequenceApi.OPENERS_PER_PRESS) {
      const batch = candidateIds.slice(at, at + sequenceApi.OPENERS_PER_PRESS);
      try {
        const drafted = await sequenceApi.draftOpeners(projectId, batch);
        if (!isOpen.current) return received;
        const byId = new Map(drafted.map((opener) => [opener.candidateId, opener.opener]));
        batch.filter((candidateId) => byId.get(candidateId)).forEach((candidateId) => received.add(candidateId));
        setReviewed((current) =>
          withEach(current, batch, (email, candidateId) => {
            const opener = byId.get(candidateId) ?? null;
            if (opener === null) return { ...email, isDrafting: false, draftFailed: true };
            if (email.openerEdited && !replaceEdited) return { ...email, isDrafting: false };
            return { ...email, opener, openerEdited: false, isDrafting: false, draftFailed: false };
          }),
        );
      } catch (error) {
        if (!isOpen.current) return received;
        toast(messageFor(error));
        setReviewed((current) =>
          withEach(current, candidateIds.slice(at), (email) => ({ ...email, isDrafting: false, draftFailed: true })),
        );
        break;
      }
    }
    return received;
  };

  const handleReview = () => {
    const fresh = chosen.filter((person) => !reviewed[person.candidateId]);
    setReviewed((current) => {
      const next = { ...current };
      for (const person of fresh) {
        next[person.candidateId] = {
          toAddress: person.emails[0].address,
          opener: "",
          openerEdited: false,
          isDrafting: false,
          draftFailed: false,
        };
      }
      return next;
    });
    setReviewingId(chosen[0]?.candidateId ?? null);
    setStep("review");
    if (fresh.length > 0) void draft(fresh.map((person) => person.candidateId));
  };

  const start = useMutation({
    mutationFn: () =>
      sequenceApi.startSequence(
        projectId,
        sequence!.id,
        chosen.map((person) => {
          const email = reviewed[person.candidateId];
          return {
            candidateId: person.candidateId,
            toAddress: email.toAddress,
            opener: email.opener.trim() || null,
            openerEdited: email.openerEdited,
          };
        }),
        {
          startMode: startChoice.mode,
          startAt: startChoice.mode === "AT" && chosenStart ? chosenStart.toISOString() : null,
        },
      ),
    onSuccess: (started) => {
      void queryClient.invalidateQueries({
        queryKey: sequenceApi.SEQUENCES_KEY(projectId),
      });
      toast(startedToastOf(started, startChoice.mode, timeZone));
      onClose();
    },
    onError: (error) => {
      toast(messageFor(error));
      const code = codeOf(error);
      if (code === "OUTREACH_PERSON_SKIPPED" || code === "OUTREACH_ALREADY_ENROLLED") {
        void people.refetch();
        setStep("choose");
      } else if (code === "MAILBOX_NOT_CONNECTED" || code === "MAILBOX_RECONNECT_NEEDED") {
        void queryClient.invalidateQueries({ queryKey: MAILBOX_KEY });
        setStep("choose");
      } else if (code === "OUTREACH_ADDRESS_NOT_ON_FILE") {
        void people.refetch();
        setStep("review");
      }
    },
  });

  // An address taken off the ledger while the dialog was open is replaced by one still on it; the
  // person's opener is kept. Without this the refused address would be resent on every press.
  useEffect(() => {
    if (!people.data) return;
    setReviewed((current) => {
      let next = current;
      for (const person of people.data) {
        const email = current[person.candidateId];
        const stillListed = person.emails.some((listed) => listed.address === email?.toAddress);
        if (email && !stillListed && person.emails.length > 0) {
          next = { ...next, [person.candidateId]: { ...email, toAddress: person.emails[0].address } };
        }
      }
      return next;
    });
  }, [people.data]);

  const reviewIndex = Math.max(
    0,
    chosen.findIndex((person) => person.candidateId === reviewingId),
  );
  const reviewing = chosen[reviewIndex] ?? null;
  const isAnyDrafting = chosen.some((person) => {
    const email = reviewed[person.candidateId];
    return email?.isDrafting && !email.openerEdited;
  });

  const footer = (
    <div className="flex w-full flex-wrap items-center gap-2">
      <span className="me-auto font-mono text-[12px] text-u-text3">
        {step === "choose" &&
          (isOverTheCap
            ? `Add at most ${sequenceApi.MAX_PEOPLE_PER_START} people at a time — untick ${chosen.length - sequenceApi.MAX_PEOPLE_PER_START}`
            : `${chosen.length} to add · ${skipped.length} skipped`)}
        {step === "review" && `Reviewing ${reviewIndex + 1} of ${chosen.length}. Edits are kept for this person only.`}
        {step === "start" && (startProblem ?? "Nothing is sent until you press Start.")}
      </span>
      {step !== "choose" && (
        <Button
          variant="secondary"
          className="px-3.5 py-2 text-[13px]"
          onClick={() => setStep(step === "start" ? "review" : "choose")}
        >
          Back
        </Button>
      )}
      {step === "choose" && (
        <Button
          className="px-3.5 py-2 text-[13px] font-semibold"
          disabled={!sequence || chosen.length === 0 || isOverTheCap || !canSend}
          onClick={handleReview}
        >
          Review {chosen.length} {chosen.length === 1 ? "email" : "emails"}
        </Button>
      )}
      {step === "review" && (
        <Button
          className="px-3.5 py-2 text-[13px] font-semibold"
          onClick={() =>
            reviewIndex < chosen.length - 1 ? setReviewingId(chosen[reviewIndex + 1].candidateId) : setStep("start")
          }
        >
          Next
        </Button>
      )}
      {step === "start" && (
        <Button
          className="px-3.5 py-2 text-[13px] font-semibold"
          loading={start.isPending}
          disabled={isAnyDrafting || startProblem !== null}
          onClick={() => start.mutate()}
        >
          Start sequence
        </Button>
      )}
    </div>
  );

  return (
    <Modal
      open
      onClose={onClose}
      title="Add to sequence"
      footer={footer}
      headerAside={<Stepper current={step} />}
      closeButton
      className={step === "review" ? "md:w-[900px]" : "md:w-[680px]"}
    >
      {step === "choose" && (
        <ChooseStep
          projectId={projectId}
          source={source}
          sequences={sequences.data ?? null}
          sequence={sequence}
          onChooseSequence={setChosenSequenceId}
          senderAddress={canSend ? connection!.address : null}
          isLoading={people.isPending}
          loadError={people.isError ? messageFor(people.error) : null}
          everyone={everyone}
          unticked={unticked}
          onToggle={(candidateId) =>
            setUnticked((current) => {
              const next = new Set(current);
              if (next.has(candidateId)) next.delete(candidateId);
              else next.add(candidateId);
              return next;
            })
          }
        />
      )}
      {step === "review" && sequence && reviewing && (
        <ReviewStep
          bookingLink={mailbox.data?.connection?.bookingLink ?? null}
          sequence={sequence}
          chosen={chosen}
          reviewed={reviewed}
          reviewing={reviewing}
          onReview={setReviewingId}
          onChange={(change) =>
            setReviewed((current) => ({
              ...current,
              [reviewing.candidateId]: {
                ...current[reviewing.candidateId],
                ...change,
              },
            }))
          }
          onRedraft={() => {
            const candidateId = reviewing.candidateId;
            void draft([candidateId], true).then((received) => {
              if (isOpen.current && received.has(candidateId)) toast("Opener redrafted.");
            });
          }}
        />
      )}
      {step === "start" && sequence && (
        <StartStep
          sequence={sequence}
          chosen={chosen}
          skipped={skipped}
          senderAddress={connection?.address ?? ""}
          timeZone={timeZone}
          choice={startChoice}
          chosenStart={chosenStart}
          onChoose={setStartChoice}
        />
      )}
    </Modal>
  );
}

function Stepper({ current }: { current: EnrolStep }) {
  const currentIndex = STEPS.findIndex((step) => step.id === current);
  return (
    <ol className="flex items-center gap-2 font-mono text-[12px] font-normal">
      {STEPS.map((step, index) => (
        <li
          key={step.id}
          className="flex items-center gap-2"
          aria-current={index === currentIndex ? "step" : undefined}
        >
          {index > 0 && <span aria-hidden="true" className="h-px w-6 bg-u-border" />}
          <span
            className={cn(
              "grid size-[18px] place-items-center rounded-full text-[10px] font-bold",
              index < currentIndex && "bg-u-accent-solid text-u-bg",
              index === currentIndex && "border-2 border-u-accent text-u-accent",
              index > currentIndex && "border border-u-border text-u-text3",
            )}
          >
            {index + 1}
          </span>
          <span className={index === currentIndex ? "text-u-text" : "text-u-text3"}>{step.label}</span>
        </li>
      ))}
    </ol>
  );
}

function ChooseStep({
  projectId,
  source,
  sequences,
  sequence,
  onChooseSequence,
  senderAddress,
  isLoading,
  loadError,
  everyone,
  unticked,
  onToggle,
}: {
  projectId: string;
  source: string;
  sequences: Sequence[] | null;
  sequence: Sequence | null;
  onChooseSequence: (sequenceId: string) => void;
  senderAddress: string | null;
  isLoading: boolean;
  loadError: string | null;
  everyone: EnrollmentCandidate[];
  unticked: ReadonlySet<string>;
  onToggle: (candidateId: string) => void;
}) {
  const byCompany = useMemo(() => {
    const groups = new Map<string, EnrollmentCandidate[]>();
    for (const person of everyone) {
      const company = person.companyName ?? "No company on file";
      groups.set(company, [...(groups.get(company) ?? []), person]);
    }
    return [...groups.entries()];
  }, [everyone]);

  return (
    <div className="pb-2">
      <div className="mb-3.5 grid gap-3 md:grid-cols-2">
        <label className="flex flex-col gap-1 text-[12px] text-u-text3">
          Sequence
          {sequences && sequences.length === 0 ? (
            <span className="text-[13px] text-u-text2">
              No sequence yet.{" "}
              <Link className="text-u-accent underline" to={`/projects/${projectId}/outreach/sequences/new`}>
                Write one first
              </Link>
            </span>
          ) : (
            <select
              value={sequence?.id ?? ""}
              onChange={(event) => onChooseSequence(event.target.value)}
              className="rounded-[6px] border border-u-border-strong bg-u-raised px-2.5 py-2 text-[13px] text-u-text"
            >
              {(sequences ?? []).map((option) => (
                <option key={option.id} value={option.id}>
                  {option.name} · {option.steps.length} {option.steps.length === 1 ? "step" : "steps"}
                </option>
              ))}
            </select>
          )}
        </label>
        <div className="flex flex-col gap-1 text-[12px] text-u-text3">
          Send from
          {senderAddress ? (
            <span className="flex items-center gap-2 rounded-[6px] border border-u-border px-2.5 py-2 font-mono text-[12.5px] text-u-text2">
              <span aria-hidden="true" className="size-[7px] rounded-full bg-u-direct" />
              {senderAddress}
            </span>
          ) : (
            <span className="text-[13px] text-u-text2">
              <Link className="text-u-accent underline" to={`/projects/${projectId}/outreach`}>
                Connect your mailbox
              </Link>{" "}
              before adding anyone.
            </span>
          )}
        </div>
      </div>
      <p className="mb-2.5 font-mono text-[12px] text-u-text3">{source}</p>
      {loadError && (
        <p role="alert" className="text-[13px] text-u-text3">
          {loadError}
        </p>
      )}
      {isLoading && <Skeleton className="h-[180px] w-full" />}
      {!isLoading && !loadError && everyone.length === 0 && (
        <p className="text-[13px] text-u-text3">Nobody is mapped here yet.</p>
      )}
      <div className="flex flex-col gap-2.5">
        {byCompany.map(([company, members]) => (
          <div key={company} className="rounded-[8px] border border-u-border">
            <div className="flex items-center gap-2 border-b border-u-border px-3 py-2 text-[12.5px] font-semibold">
              <Icon d={ICONS.building} size={13} />
              {company}
            </div>
            {members.map((person) => {
              const isSkipped = person.skipReason !== null;
              return (
                <label
                  key={person.candidateId}
                  className={cn(
                    "flex items-center gap-2.5 px-3 py-2 text-[13px]",
                    isSkipped ? "cursor-default opacity-60" : "cursor-pointer",
                  )}
                >
                  <input
                    type="checkbox"
                    disabled={isSkipped}
                    checked={!isSkipped && !unticked.has(person.candidateId)}
                    onChange={() => onToggle(person.candidateId)}
                    aria-label={person.fullName}
                  />
                  <span className="font-medium">{person.fullName}</span>
                  {person.title && (
                    <span className="truncate font-mono text-[11.5px] text-u-text3">{person.title}</span>
                  )}
                  <span
                    className={cn(
                      "ms-auto shrink-0 font-mono text-[11.5px]",
                      person.skipReason === "DO_NOT_CONTACT" ? "text-u-offlimits" : "text-u-text3",
                    )}
                  >
                    {skipNoteOf(person)}
                  </span>
                </label>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

function ReviewStep({
  bookingLink,
  sequence,
  chosen,
  reviewed,
  reviewing,
  onReview,
  onChange,
  onRedraft,
}: {
  bookingLink: string | null;
  sequence: Sequence;
  chosen: EnrollmentCandidate[];
  reviewed: Record<string, ReviewedEmail>;
  reviewing: EnrollmentCandidate;
  onReview: (candidateId: string) => void;
  onChange: (change: Partial<ReviewedEmail>) => void;
  onRedraft: () => void;
}) {
  const email = reviewed[reviewing.candidateId];
  const first = sequence.steps[0];
  const firstName = reviewing.tokens.firstName ?? reviewing.fullName;
  const parts = renderParts(first.body, withBookingLink(reviewing.tokens, bookingLink), null);
  const openerAt = parts.findIndex((part) => part.isOpener);
  const before = openerAt < 0 ? parts : parts.slice(0, openerAt);
  const after = openerAt < 0 ? [] : parts.slice(openerAt + 1);

  return (
    <div className="grid gap-4 pb-2 md:grid-cols-[230px_1fr]">
      <ul className="flex flex-col gap-1">
        {chosen.map((person) => (
          <li key={person.candidateId}>
            <button
              type="button"
              onClick={() => onReview(person.candidateId)}
              className={cn(
                "flex w-full items-center gap-2 rounded-[7px] px-2 py-1.5 text-start",
                person.candidateId === reviewing.candidateId ? "bg-u-raised" : "hover:bg-u-raised",
              )}
            >
              <Avatar id={person.personId} name={person.fullName} size="md" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[13px] font-medium">{person.fullName}</span>
                <span className="block truncate font-mono text-[11px] text-u-text3">{person.companyName ?? ""}</span>
              </span>
              {reviewed[person.candidateId]?.openerEdited && (
                <span className="rounded-full bg-u-accent-tint px-1.5 py-px font-mono text-[10px] text-u-accent">
                  Edited
                </span>
              )}
            </button>
          </li>
        ))}
      </ul>
      <div className="min-w-0 rounded-[10px] border border-u-border p-4">
        <div className="grid grid-cols-[64px_1fr] items-center gap-x-2.5 gap-y-2 border-b border-u-border pb-3 font-mono text-[12.5px] text-u-text2">
          <label htmlFor="enrol-to" className="text-u-text3">
            To
          </label>
          <select
            id="enrol-to"
            value={email.toAddress}
            onChange={(event) => onChange({ toAddress: event.target.value })}
            className="min-w-0 rounded-[6px] border border-u-border bg-u-raised px-2 py-1 font-mono text-[12.5px] text-u-text"
          >
            {reviewing.emails.map((option) => (
              <option key={option.address} value={option.address}>
                {option.address}
                {option.kind ? ` · ${option.kind}` : ""}
              </option>
            ))}
          </select>
          <span className="text-u-text3">Subject</span>
          <span className="font-medium text-u-text">
            {render(first.subject ?? "", withBookingLink(reviewing.tokens, bookingLink), null)}
          </span>
        </div>
        <div className="whitespace-pre-wrap pt-3 text-[13.5px]/[1.6] text-u-text">
          {/* The opener sits in its own box, so the blank lines around it in the template would double up. */}
          {before
            .map((part) => part.text)
            .join("")
            .replace(/\n+$/, "")}
        </div>
        {openerAt >= 0 ? (
          <div className="my-1 rounded-[8px] bg-u-inferred-tint p-2.5">
            <div className="mb-1.5 flex items-center gap-2 text-[12px] font-semibold text-u-inferred">
              <Icon d={ICONS.sparkle} size={13} />
              Opener, drafted for {firstName}
              <Button
                variant="ghost"
                className="ms-auto px-1.5 py-0.5 text-[12px]"
                loading={email.isDrafting}
                onClick={onRedraft}
              >
                ↻ Redraft
              </Button>
            </div>
            <textarea
              aria-label="Opener"
              value={email.opener}
              maxLength={600}
              rows={3}
              placeholder={openerPlaceholderOf(email)}
              onChange={(event) => onChange({ opener: event.target.value, openerEdited: true })}
              className="w-full resize-y rounded-[6px] border border-u-border bg-u-surface p-2 text-[13px]/[1.55] text-u-text outline-none"
            />
          </div>
        ) : (
          <p className="my-1 font-mono text-[11.5px] text-u-text3">This sequence's first email has no opener.</p>
        )}
        <div className="whitespace-pre-wrap text-[13.5px]/[1.6] text-u-text">
          {after
            .map((part) => part.text)
            .join("")
            .replace(/^\n+/, "")}
        </div>
        {sequence.steps.length > 1 && (
          <p className="mt-3 border-t border-u-border pt-2.5 font-mono text-[11.5px] text-u-text3">
            {followUpLine(sequence)}, each only if {firstName} hasn't replied.
          </p>
        )}
      </div>
    </div>
  );
}

function StartStep({
  sequence,
  chosen,
  skipped,
  senderAddress,
  timeZone,
  choice,
  chosenStart,
  onChoose,
}: {
  sequence: Sequence;
  chosen: EnrollmentCandidate[];
  skipped: EnrollmentCandidate[];
  senderAddress: string;
  timeZone: string;
  choice: StartChoice;
  chosenStart: Date | null;
  onChoose: (choice: StartChoice) => void;
}) {
  const reasons = [...new Set(skipped.map((person) => SKIP_SUMMARY[person.skipReason!]))];
  const [now] = useState(() => new Date());
  const city = zoneCityOf(timeZone);
  const schedule = sequence.schedule;
  const firstSend = firstSendOf(choice.mode, schedule, now, timeZone, chosenStart);
  const followUps = firstSend ? followUpDatesOf(schedule, sequence.steps, firstSend, timeZone) : [];
  const today = wallClockOf(now, timeZone).date;
  const lastDay = wallClockOf(new Date(now.getTime() + START_HORIZON_DAYS * 86_400_000), timeZone).date;
  const options: ChoiceCardOption<StartMode>[] = [
    {
      value: "NOW",
      title: "Now",
      body:
        chosen.length > 1
          ? "The first goes at once and the rest a few minutes apart, whatever the hour"
          : "It goes at once, whatever the hour",
    },
    { value: "NEXT_WINDOW", title: "Next sending window", body: `${scheduleLabelOf(schedule)} ${city} time` },
    { value: "AT", title: "Pick a date and time", body: `Up to ${START_HORIZON_DAYS} days ahead, ${city} time` },
  ];
  const handleMode = (mode: StartMode) => {
    if (mode !== "AT" || choice.date) {
      onChoose({ ...choice, mode });
      return;
    }
    const suggested = nextOpeningOf(schedule, instantOf(today, "23:59", timeZone), timeZone);
    const clock = wallClockOf(suggested, timeZone);
    onChoose({ mode, date: clock.date, time: clock.time });
  };

  return (
    <div className="pb-2">
      <div className="mb-4 flex items-center gap-3">
        <div className="grid size-10 flex-none place-items-center rounded-[10px] bg-u-accent-tint text-u-accent">
          <Icon d={ICONS.mail} size={20} />
        </div>
        <div>
          <div className="text-[15px] font-semibold">Ready to start {sequence.name}</div>
          <div className="font-mono text-[12px] text-u-text3">
            {chosen.length} {chosen.length === 1 ? "person" : "people"} · from {senderAddress}
          </div>
        </div>
      </div>
      <div className="mb-4">
        <div className="mb-2 text-[13px] font-semibold">When should the first {chosen.length === 1 ? "email" : "emails"} go?</div>
        <ChoiceCardGroup
          label="When to send"
          options={options}
          value={choice.mode}
          onChange={handleMode}
          className="sm:grid-cols-3"
        />
        {choice.mode === "AT" && (
          <div className="mt-2.5 flex flex-wrap items-center gap-2 font-mono text-[12px] text-u-text2">
            <DateInput
              value={choice.date}
              min={today}
              max={lastDay}
              ariaLabel="Start date"
              onChange={(date) => onChoose({ ...choice, date })}
              className="w-auto min-w-[150px] py-1.5"
            />
            at
            <select
              value={choice.time}
              aria-label="Start time"
              onChange={(event) => onChoose({ ...choice, time: event.target.value })}
              className="rounded-[6px] border border-u-border-strong bg-u-raised px-1.5 py-1.5 font-mono text-[12px] text-u-text"
            >
              {(HALF_HOURS.includes(choice.time) ? HALF_HOURS : [...HALF_HOURS, choice.time].sort()).map((time) => (
                <option key={time} value={time}>
                  {time}
                </option>
              ))}
            </select>
            {city} time
          </div>
        )}
        {choice.mode === "AT" && chosenStart && isOutsideSchedule(schedule, chosenStart, timeZone) && (
          <p className="mt-2 text-[12px]/[1.5] text-u-text3">
            That is outside this sequence's sending schedule ({scheduleLabelOf(schedule)}). The first emails still go
            then; follow-ups keep to the schedule.
          </p>
        )}
      </div>
      <dl className="grid grid-cols-[110px_1fr] gap-x-3 gap-y-2 text-[13px]/[1.5] md:grid-cols-[140px_1fr]">
        <dt className="text-u-text3">First emails</dt>
        <dd>{firstSendLineOf(choice.mode, firstSend, chosen.length, timeZone)}</dd>
        {sequence.steps.length > 1 && (
          <>
            <dt className="text-u-text3">Follow-ups</dt>
            <dd>
              {followUps.length > 0
                ? `${followUps
                    .map((due, index) => `${index === 0 ? "Step" : "step"} ${index + 2} on ${whenLabelOf(due, timeZone)}`)
                    .join(", ")}, for anyone who hasn't replied`
                : `${followUpLine(sequence)}, for anyone who hasn't replied`}
            </dd>
          </>
        )}
        <dt className="text-u-text3">Status</dt>
        <dd>Anyone still Identified moves to Contacted when their first email goes</dd>
        {skipped.length > 0 && (
          <>
            <dt className="text-u-text3">Skipped</dt>
            <dd>
              {skipped.length} {skipped.length === 1 ? "person" : "people"}: {reasons.join(", ")}
            </dd>
          </>
        )}
      </dl>
    </div>
  );
}

function startProblemOf(mode: StartMode, chosenStart: Date | null): string | null {
  if (mode !== "AT") return null;
  if (!chosenStart) return "Choose a day and a time.";
  if (chosenStart.getTime() <= Date.now()) return "Choose a time that is still ahead.";
  if (chosenStart.getTime() > Date.now() + START_HORIZON_DAYS * 86_400_000)
    return `Choose a time within the next ${START_HORIZON_DAYS} days.`;
  return null;
}

function firstSendLineOf(mode: StartMode, firstSend: Date | null, people: number, timeZone: string): string {
  const apart = people > 1 ? ", the rest a few minutes apart" : "";
  if (!firstSend) return "When you choose";
  if (mode === "NOW") return `Now${apart}`;
  return `${whenLabelOf(firstSend, timeZone)} ${zoneCityOf(timeZone)} time${apart}`;
}

function startedToastOf(started: StartedSequence, mode: StartMode, timeZone: string): string {
  const emails = started.enrolled === 1 ? "first email" : "first emails";
  if (mode === "NOW") return `Started. ${started.enrolled} ${emails} going out now.`;
  const first = new Date(started.firstSendAt);
  const when =
    wallClockOf(first, timeZone).date === wallClockOf(new Date(), timeZone).date
      ? `today at ${wallClockOf(first, timeZone).time}`
      : `on ${dayLabelOf(first, timeZone)} at ${wallClockOf(first, timeZone).time}`;
  return `Started. ${started.enrolled} ${emails} from ${when} ${zoneCityOf(timeZone)} time.`;
}

/** Typing is never blocked on the model: whatever is typed first wins over a draft that lands later. */
/** Until the sender's first Start makes their real link, the review says where it will go. */
function withBookingLink(tokens: RecipientTokens, bookingLink: string | null): RecipientTokens {
  return { ...tokens, bookingLink: tokens.bookingLink ?? bookingLink ?? BOOKING_LINK_PLACEHOLDER };
}

function openerPlaceholderOf(email: ReviewedEmail): string | undefined {
  if (email.isDrafting) return "Drafting an opener… or write your own.";
  if (email.draftFailed) return "The opener couldn't be drafted. Write one, or redraft.";
  return undefined;
}

function skipNoteOf(person: EnrollmentCandidate): string {
  if (person.skipReason === null) return person.emails[0]?.address ?? "";
  if (person.skipReason === "ALREADY_IN_SEQUENCE" && person.inSequence) return `Already in ${person.inSequence}`;
  return SKIP_NOTES[person.skipReason];
}

function followUpLine(sequence: Sequence): string {
  return sequence.steps
    .slice(1)
    .map((step, index) =>
      index === 0
        ? `Step 2 after ${step.delayWorkingDays} sending ${step.delayWorkingDays === 1 ? "day" : "days"}${atTimeOf(step.sendTime)}`
        : `step ${index + 2} after ${step.delayWorkingDays} more${atTimeOf(step.sendTime)}`,
    )
    .join(", ");
}

function atTimeOf(sendTime: string | null | undefined): string {
  return sendTime ? ` at ${clockOf(sendTime)}` : "";
}

function withEach(
  current: Record<string, ReviewedEmail>,
  candidateIds: string[],
  change: (email: ReviewedEmail, candidateId: string) => ReviewedEmail,
): Record<string, ReviewedEmail> {
  const next = { ...current };
  for (const candidateId of candidateIds) {
    if (next[candidateId]) next[candidateId] = change(next[candidateId], candidateId);
  }
  return next;
}
