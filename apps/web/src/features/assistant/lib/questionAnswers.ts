import type { AssistantQuestion } from "../api/types";

/** The options picked per question, by the question's place on the card. */
export type QuestionPicks = Record<number, string[]>;

/** What was typed in each question's "Other…" box, by the question's place on the card. */
export type QuestionOthers = Record<number, string>;

/** Four questions of this much each stay well inside the ask's 4,000 characters. */
export const MAX_OTHER_LENGTH = 300;

const RECOMMENDED_MARK = /\s*\(recommended\)\s*$/i;

/** The model marks the option it recommends; the mark is advice to the consultant, not part of their answer. */
export function withoutRecommendation(label: string): string {
  return label.replace(RECOMMENDED_MARK, "");
}

/**
 * The answers as the chat's next question, "Region: GCC only · Ownership: Listed, Family-owned". Every question
 * needs a choice or typed text; one left open sends nothing.
 */
export function composeAnswers(questions: AssistantQuestion[], picks: QuestionPicks, others: QuestionOthers): string {
  const parts = questions.map((question, index) => {
    const other = (others[index] ?? "").trim().slice(0, MAX_OTHER_LENGTH);
    const chosen = [...(picks[index] ?? []).map(withoutRecommendation), ...(other ? [other] : [])];
    return chosen.length > 0 ? `${question.header}: ${chosen.join(", ")}` : null;
  });
  return parts.every(Boolean) ? parts.join(" · ") : "";
}
