import { useIsMutating, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { Modal } from "../../../../components/ui/Modal";
import { LinesSkeleton } from "../../../../components/ui/Skeleton";
import { cn } from "../../../../lib/cn";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { isBillingRefusal } from "../../../billing/lib/billingView";
import type {
  InterviewPlan,
  InterviewQuestion,
  InterviewQuestionSection,
  PlanCompetency,
} from "../../api/notetakerApi";
import * as notetakerApi from "../../api/notetakerApi";
import { GAP_LABEL } from "../../lib/copilotWords";
import { meetingDayOf } from "../../lib/meetingTimes";

/** A question as the dialog edits it: the server's id when it has one, and a key of its own either way. */
interface DraftQuestion extends InterviewQuestion {
  key: string;
}

const SECTIONS: { section: InterviewQuestionSection; title: string; short: string }[] = [
  { section: "FIT", title: "Fit for the role", short: "Fit" },
  { section: "GAP", title: "Fill the gaps", short: "Gaps" },
  { section: "CLARIFY", title: "Clarify", short: "Clarify" },
];

/**
 * Prepare questions for a call (`Position.dc.html`'s Interview plan): fit questions per competency of the brief, a polite
 * question for each fact the record is missing, and a clarifier or two. Opening it drafts the plan once; after that it
 * reads back what was drafted or saved. Every question can be ticked, reworded, removed or added; Save keeps them and
 * closes, and the live copilot ticks the asked ones off during the call.
 */
export function InterviewPlanDialog({
  projectId,
  candidateId,
  meetingId,
  firstName,
  onClose,
}: {
  projectId: string;
  candidateId: string;
  meetingId: string;
  firstName: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const key = notetakerApi.INTERVIEW_PLAN_KEY(projectId, candidateId, meetingId);
  const [isDraftingElsewhere, setDraftingElsewhere] = useState(false);
  const read = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => notetakerApi.getInterviewPlan(projectId, candidateId, meetingId, signal),
    // Another tab is drafting the same plan: its answer lands here once stored.
    refetchInterval: (query) => (isDraftingElsewhere && !query.state.data?.drafted ? 2_000 : false),
  });
  const [questions, setQuestions] = useState<DraftQuestion[] | null>(null);
  const [isDirty, setDirty] = useState(false);
  const [isConfirmingClose, setConfirmingClose] = useState(false);

  const adopt = (plan: InterviewPlan) => {
    queryClient.setQueryData(key, plan);
    setQuestions(plan.questions.map(draftOf));
    setDirty(false);
  };
  // The draft's state is read from the cache and its own callbacks, never `draft.isPending`: a draft started as the
  // dialog mounts outlived StrictMode's rehearsal unmount, which detaches the observer for good, and the progress spun
  // on over a stored plan.
  const draftKey = notetakerApi.INTERVIEW_PLAN_DRAFT_KEY(projectId, candidateId, meetingId);
  const isDraftPending = useIsMutating({ mutationKey: draftKey }) > 0;
  const [draftFailure, setDraftFailure] = useState<unknown>(null);
  const draft = useMutation({
    mutationKey: draftKey,
    mutationFn: () => notetakerApi.draftInterviewPlan(projectId, candidateId, meetingId),
    onSuccess: adopt,
    onError: (error) => {
      if (codeOf(error) === "INTERVIEW_PLAN_DRAFTING") {
        setDraftingElsewhere(true);
        return;
      }
      if (isBillingRefusal(error)) {
        onClose();
        return;
      }
      setDraftFailure(error);
    },
  });
  const save = useMutation({
    mutationFn: (edited: DraftQuestion[]) =>
      notetakerApi.saveInterviewPlan(projectId, candidateId, meetingId, edited.map(questionOf)),
    onSuccess: (plan) => {
      adopt(plan);
      toast(`Questions for ${firstName} saved.`);
      onClose();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const plan = read.data;
  useEffect(() => {
    if (plan?.drafted && questions === null) setQuestions(plan.questions.map(draftOf));
  }, [plan, questions]);
  const shouldDraft = read.isSuccess && plan?.drafted === false && !isDraftingElsewhere && !isDraftPending;
  const draftedOnce = useRef(false);
  useEffect(() => {
    if (shouldDraft && !draftedOnce.current) {
      draftedOnce.current = true;
      draft.mutate();
    }
  }, [shouldDraft, draft]);

  const failed = read.isError || draftFailure !== null;
  const isReading = !failed && (questions === null || isDraftPending);
  const isDrafting = isReading && (isDraftPending || isDraftingElsewhere || shouldDraft);
  const update = (change: (held: DraftQuestion[]) => DraftQuestion[]) => {
    setQuestions((held) => (held ? change(held) : held));
    setDirty(true);
  };
  const ticked = questions?.filter((question) => question.included).length ?? 0;
  const hasBlank = questions?.some((question) => question.text.trim() === "") ?? false;
  const isSaved = plan?.savedAt != null && !isDirty;

  const handleClose = () => (isDirty ? setConfirmingClose(true) : onClose());

  const handleCopy = () => {
    const text = (questions ?? [])
      .filter((question) => question.included)
      .map((question, index) => `${index + 1}. ${question.text}`)
      .join("\n");
    navigator.clipboard?.writeText(text).then(
      () => toast("Questions copied."),
      () => toast.error("Couldn't copy the questions."),
    );
  };

  return (
    <>
      <Modal
        open
        onClose={handleClose}
        title="Interview plan"
        subtitle={plan ? `${firstName} · ${plan.title ?? "Call"} · ${meetingDayOf(plan.startsAt)}` : undefined}
        closeButton
        className="md:w-[600px]"
        footer={
          failed ? (
            <>
              <Button variant="secondary" size="sm" onClick={onClose}>
                Close
              </Button>
              {codeOf(draftFailure) !== "VALIDATION_FAILED" && (
                <Button
                  size="sm"
                  onClick={() => {
                    setDraftFailure(null);
                    setDraftingElsewhere(false);
                    draft.mutate();
                  }}
                >
                  Try again
                </Button>
              )}
            </>
          ) : isReading ? (
            <Button variant="secondary" size="sm" onClick={onClose}>
              Cancel
            </Button>
          ) : (
            <div className="flex w-full flex-wrap items-center gap-x-3 gap-y-2">
              <span className="text-note text-u-text3" aria-live="polite">
                {ticked} of {questions?.length ?? 0} to ask
                {isDirty ? " · Unsaved changes" : isSaved ? " · Saved" : ""}
              </span>
              <span className="flex-1" />
              <Button
                variant="ghost"
                size="sm"
                disabled={save.isPending || isDirty}
                title={
                  isDirty
                    ? "Save or discard your changes first"
                    : "Writes fresh questions from the brief and the record; questions you added are kept"
                }
                onClick={() => draft.mutate()}
              >
                Regenerate
              </Button>
              <Button variant="secondary" size="sm" disabled={ticked === 0} onClick={handleCopy}>
                <Icon d={ICONS.copy} size={12} />
                Copy
              </Button>
              {isSaved ? (
                <Button size="sm" onClick={onClose}>
                  Done
                </Button>
              ) : (
                <Button
                  size="sm"
                  disabled={hasBlank}
                  loading={save.isPending}
                  onClick={() => questions && save.mutate(questions)}
                >
                  Save questions
                </Button>
              )}
            </div>
          )
        }
      >
        {failed && (
          <p className="py-6 text-body text-u-text2" role="alert">
            {codeOf(draftFailure ?? read.error) === "AI_UNAVAILABLE"
              ? "Couldn't prepare the questions just now."
              : messageFor(draftFailure ?? read.error)}
          </p>
        )}
        {isReading && (isDrafting ? <DraftingProgress firstName={firstName} /> : <LinesSkeleton />)}
        {!failed && !isReading && questions && plan && (
          <PlanBody
            plan={plan}
            questions={questions}
            firstName={firstName}
            onChange={(changed) =>
              update((held) => held.map((question) => (question.key === changed.key ? changed : question)))
            }
            onRemove={(removed) => update((held) => held.filter((question) => question.key !== removed.key))}
            onAdd={(section) => update((held) => [...held, blankOf(section)])}
          />
        )}
      </Modal>
      <ConfirmDialog
        open={isConfirmingClose}
        title="Discard your changes?"
        confirmLabel="Discard"
        cancelLabel="Keep editing"
        onConfirm={() => {
          setConfirmingClose(false);
          onClose();
        }}
        onClose={() => setConfirmingClose(false)}
      >
        <p>The questions you ticked, reworded or added since the last save are lost.</p>
      </ConfirmDialog>
    </>
  );
}

/** Drafting is one model call with no progress of its own; the steps say what it is doing, paced on a clock. */
function DraftingProgress({ firstName }: { firstName: string }) {
  const steps = ["Reading the brief", `Checking ${firstName}'s record for gaps`, "Writing the questions"];
  const [current, setCurrent] = useState(0);
  useEffect(() => {
    const timers = [setTimeout(() => setCurrent(1), 2_500), setTimeout(() => setCurrent(2), 6_000)];
    return () => timers.forEach(clearTimeout);
  }, []);
  return (
    <div className="flex flex-col gap-4 py-6" aria-live="polite">
      <p className="text-body text-u-text2">Preparing questions for {firstName}. This takes a few seconds.</p>
      <ol className="flex flex-col gap-2.5">
        {steps.map((step, index) => (
          <li
            key={step}
            className={cn(
              "flex items-center gap-2.5 text-body",
              index < current ? "text-u-text2" : index === current ? "text-u-text" : "text-u-text3",
            )}
          >
            <span aria-hidden="true" className="grid size-4 flex-none place-items-center">
              {index < current ? (
                <Icon d={ICONS.check} size={14} className="text-u-direct" />
              ) : index === current ? (
                <span className="size-3.5 animate-spin rounded-full border-2 border-u-accent-ring border-t-u-accent" />
              ) : (
                <span className="size-1.5 rounded-full bg-u-border-strong" />
              )}
            </span>
            {step}
          </li>
        ))}
      </ol>
    </div>
  );
}

function PlanBody({
  plan,
  questions,
  firstName,
  onChange,
  onRemove,
  onAdd,
}: {
  plan: InterviewPlan;
  questions: DraftQuestion[];
  firstName: string;
  onChange: (question: DraftQuestion) => void;
  onRemove: (question: DraftQuestion) => void;
  onAdd: (section: InterviewQuestionSection) => void;
}) {
  const sectionRefs = useRef<Partial<Record<InterviewQuestionSection, HTMLElement | null>>>({});
  const hints: Record<InterviewQuestionSection, string> = {
    FIT: `How ${firstName} matches the brief. Competencies the notes don't show yet come first.`,
    GAP: `Facts missing from ${firstName}'s record. The call is the chance to fill them.`,
    CLARIFY: `Points in ${firstName}'s career worth a closer look.`,
  };
  return (
    <div className="flex flex-col gap-6 pb-3">
      <div className="flex flex-col gap-2">
        <p className="text-note text-u-text2">Tick what you'll ask. Click a question to reword it.</p>
        <nav aria-label="Parts of the plan" className="flex flex-wrap gap-1.5">
          {SECTIONS.map(({ section, short }) => (
            <button
              key={section}
              type="button"
              onClick={() => sectionRefs.current[section]?.scrollIntoView?.({ behavior: "smooth", block: "start" })}
              className="inline-flex items-center gap-1.5 rounded-u-control border border-u-border px-2 py-0.5 text-note text-u-text2 hover:border-u-border-strong hover:text-u-text"
            >
              {short}
              <span className="font-mono text-meta text-u-text3">
                {questions.filter((question) => question.section === section).length}
              </span>
            </button>
          ))}
        </nav>
      </div>
      {SECTIONS.map(({ section, title }) => {
        const under = questions.filter((question) => question.section === section);
        const asking = under.filter((question) => question.included).length;
        return (
          <section
            key={section}
            ref={(element) => {
              sectionRefs.current[section] = element;
            }}
            aria-label={title}
            className="flex scroll-mt-2 flex-col gap-1"
          >
            <div className="flex items-baseline gap-2 border-b border-u-border pb-1.5">
              <h3 className="type-heading">{title}</h3>
              <span className="ms-auto text-note text-u-text3">
                {under.length === 0 ? "None" : `${asking} of ${under.length} to ask`}
              </span>
            </div>
            <p className="pb-1 pt-1.5 text-note text-u-text3">{hints[section]}</p>
            {section === "GAP" && plan.missing.length > 0 && (
              <p className="pb-1 text-note text-u-text2">
                <span className="text-u-text3">Missing on record:</span>{" "}
                {plan.missing.map((gap) => GAP_LABEL[gap]).join(", ")}
              </p>
            )}
            {section === "FIT" ? (
              <FitQuestions plan={plan} questions={under} onChange={onChange} onRemove={onRemove} />
            ) : (
              under.map((question) => (
                <QuestionRow key={question.key} question={question} onChange={onChange} onRemove={onRemove} />
              ))
            )}
            {section === "GAP" && plan.missing.length === 0 && under.length === 0 && (
              <p className="text-note text-u-text3">The record holds every fact a call usually fills.</p>
            )}
            <button
              type="button"
              onClick={() => onAdd(section)}
              className="mt-1 inline-flex items-center gap-1 self-start rounded-u-control px-1 py-0.5 text-note font-medium text-u-text3 hover:text-u-text"
            >
              <Icon d={ICONS.plus} size={12} />
              Add a question
            </button>
          </section>
        );
      })}
    </div>
  );
}

function FitQuestions({
  plan,
  questions,
  onChange,
  onRemove,
}: {
  plan: InterviewPlan;
  questions: DraftQuestion[];
  onChange: (question: DraftQuestion) => void;
  onRemove: (question: DraftQuestion) => void;
}) {
  const rows = (list: DraftQuestion[]) =>
    list.map((question) => (
      <QuestionRow key={question.key} question={question} onChange={onChange} onRemove={onRemove} />
    ));
  return (
    <>
      {orderedCompetencies(plan.competencies).map((competency) => {
        const under = questions.filter((question) => question.competency === competency.name);
        if (under.length === 0) return null;
        return (
          <div key={competency.name} className="flex flex-col pt-2 first:pt-0">
            <p className="flex items-baseline gap-2 text-note font-medium text-u-text2">
              {competency.name}
              <span className="font-mono text-meta font-normal text-u-text3">{competency.weight}%</span>
              {competency.evidenced && <span className="font-normal text-u-text3">· already in notes</span>}
            </p>
            {rows(under)}
          </div>
        );
      })}
      {rows(questions.filter((question) => !plan.competencies.some((c) => c.name === question.competency)))}
    </>
  );
}

function QuestionRow({
  question,
  onChange,
  onRemove,
}: {
  question: DraftQuestion;
  onChange: (question: DraftQuestion) => void;
  onRemove: (question: DraftQuestion) => void;
}) {
  const [isEditing, setEditing] = useState(question.text === "");
  const details = [
    question.listenFor && { label: "Listen for", text: question.listenFor },
    question.gap && { label: "Fills", text: GAP_LABEL[question.gap] },
    question.why && { label: "Why", text: question.why },
  ].filter((detail): detail is { label: string; text: string } => Boolean(detail));
  return (
    <div className="group -mx-2 flex items-start gap-2.5 rounded-u-control px-2 py-1.5 hover:bg-u-raised">
      <input
        type="checkbox"
        checked={question.included}
        onChange={(event) => onChange({ ...question, included: event.target.checked })}
        aria-label="Ask this question"
        className="mt-[3px] size-3.5 flex-none accent-u-accent"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        {isEditing ? (
          <textarea
            // A new or reworded question is typed straight away.
            autoFocus
            aria-label="Question"
            value={question.text}
            maxLength={400}
            rows={2}
            placeholder="Type your question"
            onChange={(event) => onChange({ ...question, text: event.target.value })}
            onBlur={() => question.text.trim() !== "" && setEditing(false)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                event.currentTarget.blur();
              }
            }}
            className="min-w-0 resize-y rounded-u-control border border-u-border-strong bg-u-bg px-2 py-1 text-body"
          />
        ) : (
          <button
            type="button"
            title="Click to reword"
            onClick={() => setEditing(true)}
            className={cn(
              "cursor-text text-start text-body",
              question.included ? "text-u-text" : "text-u-text3 line-through",
            )}
          >
            {question.text}
          </button>
        )}
        {details.map((detail) => (
          <p key={detail.label} className="text-note text-u-text3">
            <span className="text-u-text2">{detail.label}:</span> {detail.text}
          </p>
        ))}
      </div>
      <button
        type="button"
        aria-label="Remove question"
        onClick={() => onRemove(question)}
        className="grid size-6 flex-none place-items-center rounded-u-control text-u-text3 opacity-0 hover:bg-u-sunken hover:text-u-text focus-visible:opacity-100 group-hover:opacity-100"
      >
        <Icon d={ICONS.close} size={12} />
      </button>
    </div>
  );
}

/** Competencies the record does not show yet come first, heaviest first, where proving them matters most. */
function orderedCompetencies(competencies: PlanCompetency[]): PlanCompetency[] {
  return [...competencies].sort(
    (first, second) => Number(first.evidenced) - Number(second.evidenced) || second.weight - first.weight,
  );
}

let blankCount = 0;

function blankOf(section: InterviewQuestionSection): DraftQuestion {
  blankCount += 1;
  return {
    key: `new-${blankCount}`,
    id: null,
    section,
    panel: null,
    competency: null,
    gap: null,
    text: "",
    listenFor: null,
    why: null,
    included: true,
    source: "MANUAL",
  };
}

function draftOf(question: InterviewQuestion): DraftQuestion {
  return { ...question, key: question.id ?? `new-${(blankCount += 1)}` };
}

function questionOf({ key: _key, ...question }: DraftQuestion): InterviewQuestion {
  return { ...question, text: question.text.trim() };
}
