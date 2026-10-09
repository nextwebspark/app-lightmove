import { useId, useRef, useState, type KeyboardEvent } from "react";
import { cn } from "../../../lib/cn";
import type { AssistantQuestion } from "../api/types";
import { composeAnswers, MAX_OTHER_LENGTH, type QuestionOthers, type QuestionPicks } from "../lib/questionAnswers";

/**
 * The questions an answer asked in place of answering. The consultant's choices go back as the chat's next
 * question; once a later turn exists the card only reads, since the answer is the bubble under it.
 */
export function AssistantQuestionCard({
  questions,
  answerable,
  onAnswer,
}: {
  questions: AssistantQuestion[];
  answerable: boolean;
  onAnswer: (answers: string) => void;
}) {
  const [picks, setPicks] = useState<QuestionPicks>({});
  const [others, setOthers] = useState<QuestionOthers>({});
  const composed = composeAnswers(questions, picks, others);

  const handlePick = (index: number, label: string) => {
    if (!answerable) return;
    const multiSelect = questions[index].multiSelect;
    setPicks((current) => {
      const held = current[index] ?? [];
      const next = multiSelect
        ? held.includes(label) ? held.filter((picked) => picked !== label) : [...held, label]
        : [label];
      return { ...current, [index]: next };
    });
    if (!multiSelect) setOthers((current) => ({ ...current, [index]: "" }));
  };

  const handleType = (index: number, text: string) => {
    setOthers((current) => ({ ...current, [index]: text }));
    if (!questions[index].multiSelect && text) setPicks((current) => ({ ...current, [index]: [] }));
  };

  return (
    <div
      className={cn(
        "overflow-hidden rounded-[11px] border bg-u-surface",
        answerable ? "border-u-accent shadow-u-e3" : "border-u-border",
      )}
    >
      {questions.map((question, index) => (
        // The model may repeat a question, and picks are held by place, so the place is the key.
        <QuestionBlock
          key={index}
          question={question}
          picked={picks[index] ?? []}
          other={others[index] ?? ""}
          answerable={answerable}
          onPick={(label) => handlePick(index, label)}
          onType={(text) => handleType(index, text)}
        />
      ))}
      <div className="flex items-center gap-2 bg-u-raised px-3 py-[9px]">
        <p className="font-mono text-[11px] text-u-text3">
          {!answerable ? "Answered below" : composed ? "Ready to send" : "Pick an answer to each"}
        </p>
        {answerable && (
          <button
            type="button"
            disabled={!composed}
            onClick={() => composed && onAnswer(composed)}
            className="ms-auto rounded-md bg-[linear-gradient(135deg,var(--color-u-inferred),var(--color-u-adjacent))] px-[11px] py-1.5 font-sans text-[11.5px] font-medium text-white transition disabled:opacity-40"
          >
            Send answers
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * One question: a radio group (arrow keys move and pick, one stop in the tab order) or a group of
 * checkboxes, then the "Other…" box.
 */
function QuestionBlock({
  question,
  picked,
  other,
  answerable,
  onPick,
  onType,
}: {
  question: AssistantQuestion;
  picked: string[];
  other: string;
  answerable: boolean;
  onPick: (label: string) => void;
  onType: (text: string) => void;
}) {
  const labelId = useId();
  const options = useRef<(HTMLButtonElement | null)[]>([]);
  const radio = !question.multiSelect;
  const firstPicked = question.options.findIndex((option) => picked.includes(option.label));
  const tabStop = firstPicked === -1 ? 0 : firstPicked;

  const handleKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (!radio) return;
    const step = event.key === "ArrowDown" || event.key === "ArrowRight" ? 1
      : event.key === "ArrowUp" || event.key === "ArrowLeft" ? -1 : 0;
    if (step === 0) return;
    event.preventDefault();
    const next = (index + step + question.options.length) % question.options.length;
    options.current[next]?.focus();
    onPick(question.options[next].label);
  };

  return (
    <div className="border-b border-u-border px-3 py-2.5">
      <div className="flex items-center gap-[7px]">
        <span className="rounded bg-u-inferred-tint px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.04em] text-u-inferred">
          {question.header}
        </span>
        {question.multiSelect && <span className="font-mono text-[10px] text-u-text3">Pick any</span>}
      </div>
      <p id={labelId} className="mb-2 mt-1.5 font-sans text-[12.5px] font-medium leading-[1.45] text-u-text">
        {question.question}
      </p>
      <div role={radio ? "radiogroup" : "group"} aria-labelledby={labelId}>
        {question.options.map((option, index) => {
          const on = picked.includes(option.label);
          return (
            <button
              key={index}
              ref={(element) => {
                options.current[index] = element;
              }}
              type="button"
              role={radio ? "radio" : "checkbox"}
              aria-checked={on}
              disabled={!answerable}
              tabIndex={radio && index !== tabStop ? -1 : 0}
              onClick={() => onPick(option.label)}
              onKeyDown={(event) => handleKeyDown(event, index)}
              className={cn(
                "mb-[5px] flex w-full items-start gap-[9px] rounded-[7px] border px-[9px] py-[7px] text-start transition",
                on ? "border-u-inferred bg-u-inferred-tint" : "border-u-border",
                answerable ? "hover:border-u-inferred" : "cursor-default opacity-55",
              )}
            >
              <span
                aria-hidden="true"
                className={cn(
                  "mt-0.5 grid h-[13px] w-[13px] flex-none place-items-center shadow-[inset_0_0_0_1.5px_currentColor]",
                  radio ? "rounded-full" : "rounded-[3px]",
                  on ? "text-u-inferred" : "text-u-border-strong",
                )}
              >
                <span
                  className={cn("h-[7px] w-[7px] bg-current", radio ? "rounded-full" : "rounded-[2px]", !on && "opacity-0")}
                />
              </span>
              <span className="min-w-0">
                <span className="block font-sans text-xs font-medium text-u-text">{option.label}</span>
                {option.description && (
                  <span className="block font-sans text-[11px] leading-[1.4] text-u-text3">{option.description}</span>
                )}
              </span>
            </button>
          );
        })}
      </div>
      {answerable && (
        <input
          value={other}
          onChange={(event) => onType(event.target.value)}
          maxLength={MAX_OTHER_LENGTH}
          placeholder="Other…"
          aria-label={`Another answer to: ${question.question}`}
          className="w-full rounded-[7px] border border-dashed border-u-border-strong bg-transparent px-[9px] py-[7px] font-sans text-xs text-u-text outline-none focus:border-u-inferred"
        />
      )}
    </div>
  );
}
