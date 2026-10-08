import { useState } from "react";
import { cn } from "../../../lib/cn";
import type { AssistantQuestion } from "../api/types";

type Picks = Record<number, string[]>;
type Typed = Record<number, string>;

/**
 * The questions an answer asked in place of answering. The consultant's choices go back as the chat's next
 * question ("Region: GCC only · Ownership: Listed, Family-owned"); once a later turn exists the card only
 * reads, since the answer is the bubble under it.
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
  const [picks, setPicks] = useState<Picks>({});
  const [typed, setTyped] = useState<Typed>({});
  const composed = composeAnswers(questions, picks, typed);

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
    if (!multiSelect) setTyped((current) => ({ ...current, [index]: "" }));
  };

  const handleType = (index: number, text: string) => {
    setTyped((current) => ({ ...current, [index]: text }));
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
        <fieldset key={question.question} className="border-b border-u-border px-3 py-2.5">
          <legend className="sr-only">{question.question}</legend>
          <div className="flex items-center gap-[7px]">
            <span className="rounded bg-u-inferred-tint px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.04em] text-u-inferred">
              {question.header}
            </span>
            {question.multiSelect && <span className="font-mono text-[10px] text-u-text3">Pick any</span>}
          </div>
          <p aria-hidden="true" className="mb-2 mt-1.5 font-sans text-[12.5px] font-medium leading-[1.45] text-u-text">
            {question.question}
          </p>
          {question.options.map((option) => {
            const on = (picks[index] ?? []).includes(option.label);
            return (
              <button
                key={option.label}
                type="button"
                role={question.multiSelect ? "checkbox" : "radio"}
                aria-checked={on}
                disabled={!answerable}
                onClick={() => handlePick(index, option.label)}
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
                    question.multiSelect ? "rounded-[3px]" : "rounded-full",
                    on ? "text-u-inferred" : "text-u-border-strong",
                  )}
                >
                  <span
                    className={cn(
                      "h-[7px] w-[7px] bg-current",
                      question.multiSelect ? "rounded-[2px]" : "rounded-full",
                      !on && "opacity-0",
                    )}
                  />
                </span>
                <span className="min-w-0">
                  <span className="block font-sans text-xs font-medium text-u-text">{option.label}</span>
                  <span className="block font-sans text-[11px] leading-[1.4] text-u-text3">{option.description}</span>
                </span>
              </button>
            );
          })}
          {answerable && (
            <input
              value={typed[index] ?? ""}
              onChange={(event) => handleType(index, event.target.value)}
              placeholder="Other…"
              aria-label={`Another answer to: ${question.question}`}
              className="w-full rounded-[7px] border border-dashed border-u-border-strong bg-transparent px-[9px] py-[7px] font-sans text-xs text-u-text outline-none focus:border-u-inferred"
            />
          )}
        </fieldset>
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

const RECOMMENDED_MARK = /\s*\(recommended\)\s*$/i;

/** The model marks the option it recommends; the mark is advice to the consultant, not part of their answer. */
function withoutRecommendation(label: string): string {
  return label.replace(RECOMMENDED_MARK, "");
}

/** Every question needs a choice or typed text; one left open sends nothing. */
export function composeAnswers(questions: AssistantQuestion[], picks: Picks, typed: Typed): string {
  const parts = questions.map((question, index) => {
    const other = (typed[index] ?? "").trim();
    const chosen = [...(picks[index] ?? []).map(withoutRecommendation), ...(other ? [other] : [])];
    return chosen.length > 0 ? `${question.header}: ${chosen.join(", ")}` : null;
  });
  return parts.every(Boolean) ? parts.join(" · ") : "";
}
