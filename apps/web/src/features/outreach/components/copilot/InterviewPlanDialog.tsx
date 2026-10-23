import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, useToast } from "../../../../components/ui";
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

const SECTIONS: { section: InterviewQuestionSection; title: string; hint: string }[] = [
  { section: "FIT", title: "Fit for the role", hint: "From the brief's competencies" },
  { section: "GAP", title: "Fill the gaps", hint: "Only what the record is missing" },
  { section: "CLARIFY", title: "Clarify", hint: "From the career on file" },
];

/**
 * Prepare questions for a call (`Position.dc.html`'s Interview plan): fit questions per competency of the brief, a polite
 * question for each fact the record is missing, and a clarifier or two. Opening it drafts the plan once; after that it
 * reads back what was saved. Every question can be ticked, edited, removed or added before Save, and the live copilot
 * ticks the asked ones off during the call.
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

  const adopt = (plan: InterviewPlan) => {
    queryClient.setQueryData(key, plan);
    setQuestions(plan.questions.map(draftOf));
    setDirty(false);
  };
  const draft = useMutation({
    mutationFn: () => notetakerApi.draftInterviewPlan(projectId, candidateId, meetingId),
    onSuccess: adopt,
    onError: (error) => {
      if (codeOf(error) === "INTERVIEW_PLAN_DRAFTING") {
        setDraftingElsewhere(true);
        return;
      }
      if (isBillingRefusal(error)) onClose();
    },
  });
  const save = useMutation({
    mutationFn: (edited: DraftQuestion[]) =>
      notetakerApi.saveInterviewPlan(projectId, candidateId, meetingId, edited.map(questionOf)),
    onSuccess: (plan) => {
      adopt(plan);
      toast(`Questions for ${firstName} saved.`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const plan = read.data;
  useEffect(() => {
    if (plan?.drafted && questions === null) setQuestions(plan.questions.map(draftOf));
  }, [plan, questions]);
  const shouldDraft = read.isSuccess && plan?.drafted === false && !isDraftingElsewhere;
  const draftedOnce = useRef(false);
  useEffect(() => {
    if (shouldDraft && !draftedOnce.current) {
      draftedOnce.current = true;
      draft.mutate();
    }
  }, [shouldDraft, draft]);

  const failed = read.isError || (draft.isError && !isDraftingElsewhere && !isBillingRefusal(draft.error));
  const isReading = !failed && (questions === null || draft.isPending);
  const update = (change: (held: DraftQuestion[]) => DraftQuestion[]) => {
    setQuestions((held) => (held ? change(held) : held));
    setDirty(true);
  };
  const ticked = questions?.filter((question) => question.included).length ?? 0;

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
    <Modal
      open
      onClose={onClose}
      title="Interview plan"
      subtitle={plan ? `${firstName} · ${plan.title ?? "Call"} · ${meetingDayOf(plan.startsAt)}` : undefined}
      closeButton
      className="md:w-[560px]"
      footer={
        failed ? (
          <>
            <Button variant="secondary" size="sm" onClick={onClose}>
              Close
            </Button>
            {codeOf(draft.error) !== "VALIDATION_FAILED" && (
              <Button
                size="sm"
                onClick={() => {
                  draft.reset();
                  setDraftingElsewhere(false);
                  draft.mutate();
                }}
              >
                Try again
              </Button>
            )}
          </>
        ) : (
          <div className="flex w-full flex-wrap items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              disabled={isReading || save.isPending}
              title="Writes fresh questions from the brief and the record; your own questions are kept"
              onClick={() => draft.mutate()}
            >
              <Icon d={ICONS.sparkle} size={12} />
              Regenerate
            </Button>
            {questions && (
              <span className="text-note text-u-text3">
                {ticked} of {questions.length} ticked
              </span>
            )}
            <span className="flex-1" />
            <Button variant="secondary" size="sm" disabled={isReading || ticked === 0} onClick={handleCopy}>
              Copy as text
            </Button>
            <Button
              size="sm"
              disabled={isReading || !isDirty || questions?.some((question) => question.text.trim() === "")}
              loading={save.isPending}
              onClick={() => questions && save.mutate(questions)}
            >
              Save
            </Button>
          </div>
        )
      }
    >
      {failed && (
        <p className="py-6 text-body text-u-text2" role="alert">
          {codeOf(draft.error ?? read.error) === "AI_UNAVAILABLE"
            ? "Couldn't prepare the questions just now."
            : messageFor(draft.error ?? read.error)}
        </p>
      )}
      {isReading && (
        <div className="flex flex-col gap-3 py-4" aria-live="polite">
          <p className="flex items-center gap-2 text-body text-u-text2">
            <span
              aria-hidden="true"
              className="size-3.5 animate-spin rounded-full border-2 border-u-accent-ring border-t-u-accent"
            />
            Reading the brief and {firstName}'s record…
          </p>
          <LinesSkeleton />
        </div>
      )}
      {!failed && !isReading && questions && plan && (
        <div className="flex flex-col gap-5 pb-2">
          {SECTIONS.map(({ section, title, hint }) => (
            <PlanSection
              key={section}
              section={section}
              title={title}
              hint={hint}
              plan={plan}
              questions={questions.filter((question) => question.section === section)}
              onChange={(changed) =>
                update((held) => held.map((question) => (question.key === changed.key ? changed : question)))
              }
              onRemove={(removed) => update((held) => held.filter((question) => question.key !== removed.key))}
              onAdd={() => update((held) => [...held, blankOf(section)])}
            />
          ))}
        </div>
      )}
    </Modal>
  );
}

function PlanSection({
  section,
  title,
  hint,
  plan,
  questions,
  onChange,
  onRemove,
  onAdd,
}: {
  section: InterviewQuestionSection;
  title: string;
  hint: string;
  plan: InterviewPlan;
  questions: DraftQuestion[];
  onChange: (question: DraftQuestion) => void;
  onRemove: (question: DraftQuestion) => void;
  onAdd: () => void;
}) {
  const rows = (list: DraftQuestion[]) =>
    list.map((question) => (
      <QuestionRow key={question.key} question={question} onChange={onChange} onRemove={onRemove} />
    ));
  return (
    <section aria-label={title} className="flex flex-col gap-2">
      <h3 className="flex items-baseline gap-2">
        <span className="text-body font-semibold">{title}</span>
        <span className="text-note text-u-text3">{hint}</span>
      </h3>
      {section === "FIT" ? (
        <>
          {orderedCompetencies(plan.competencies).map((competency) => {
            const under = questions.filter((question) => question.competency === competency.name);
            if (under.length === 0) return null;
            return (
              <div
                key={competency.name}
                className="flex flex-col gap-1 rounded-u-card border border-u-border bg-u-bg p-2.5"
              >
                <p className="flex items-center gap-2 text-note font-medium">
                  {competency.name}
                  <span className="font-mono text-meta text-u-text3">{competency.weight}%</span>
                  {competency.evidenced && (
                    <span className="ms-auto rounded-full bg-u-direct-tint px-2 py-px text-meta text-u-direct">
                      Already evidenced in notes
                    </span>
                  )}
                </p>
                {rows(under)}
              </div>
            );
          })}
          {rows(questions.filter((question) => !plan.competencies.some((c) => c.name === question.competency)))}
        </>
      ) : (
        rows(questions)
      )}
      {section === "GAP" && plan.missing.length === 0 && questions.length === 0 && (
        <p className="text-note text-u-text3">The record holds every fact a call usually fills.</p>
      )}
      <button
        type="button"
        onClick={onAdd}
        className="self-start text-note font-medium text-u-accent hover:underline hover:underline-offset-2"
      >
        + Add your own
      </button>
    </section>
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
  const checkboxId = `plan-question-${question.key}`;
  return (
    <div className="group flex flex-col gap-0.5 py-1">
      <div className="flex items-start gap-2.5">
        <input
          id={checkboxId}
          type="checkbox"
          checked={question.included}
          onChange={(event) => onChange({ ...question, included: event.target.checked })}
          aria-label="Ask this question"
          className="mt-1 size-3.5 flex-none accent-u-accent"
        />
        {isEditing ? (
          <textarea
            // A new or edited question is typed straight away.
            autoFocus
            aria-label="Question"
            value={question.text}
            maxLength={400}
            rows={2}
            onChange={(event) => onChange({ ...question, text: event.target.value })}
            onBlur={() => question.text.trim() !== "" && setEditing(false)}
            className="min-w-0 flex-1 resize-y rounded-u-control border border-u-border-strong bg-u-bg px-2 py-1 text-body"
          />
        ) : (
          <label
            htmlFor={checkboxId}
            className={cn("min-w-0 flex-1 text-body", !question.included && "text-u-text3 line-through")}
          >
            {question.text}
          </label>
        )}
        <span className="flex flex-none gap-0.5 opacity-60 group-focus-within:opacity-100 group-hover:opacity-100">
          <button
            type="button"
            aria-label="Edit question"
            onClick={() => setEditing(true)}
            className="grid size-6 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.pencil} size={12} />
          </button>
          <button
            type="button"
            aria-label="Remove question"
            onClick={() => onRemove(question)}
            className="grid size-6 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={12} />
          </button>
        </span>
      </div>
      {question.listenFor && (
        <p className="ps-6 text-note text-u-text3">
          <span className="font-medium text-u-text2">Listen for:</span> {question.listenFor}
        </p>
      )}
      {question.gap && <p className="ps-6 text-note text-u-text3">For the record: {GAP_LABEL[question.gap]}</p>}
      {question.why && <p className="ps-6 text-note text-u-text3">{question.why}</p>}
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
