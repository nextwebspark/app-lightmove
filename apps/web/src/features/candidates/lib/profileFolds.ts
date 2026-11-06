import type { CandidateCompensation, PersonDocument } from "../api/types";
import { formatAmount } from "./compensation";

/** The Compensation fold's folded line: the base and the notice period, the two a consultant asks first. */
export function compensationFoldSummary(compensation: CandidateCompensation): string | null {
  const facts = [
    formatAmount(compensation.currency ?? "", compensation.baseSalary),
    compensation.noticePeriod ? `Notice: ${compensation.noticePeriod.toLowerCase()}` : null,
  ].filter((fact): fact is string => Boolean(fact));
  return facts.length > 0 ? facts.join(" · ") : null;
}

/** The Documents fold's folded line: the primary CV first, then the rest by title. */
export function documentsFoldSummary(documents: readonly PersonDocument[] | undefined): string | null {
  if (!documents || documents.length === 0) return null;
  const ordered = [...documents].sort((left, right) => Number(right.primaryCv) - Number(left.primaryCv));
  return ordered.map((document) => document.title).join(" · ");
}

const FOLD_FIND_ATTEMPTS = 20;
const FOLD_FIND_INTERVAL_MS = 50;

/** Scrolls a fold into view once it is drawn — it may be on a tab that is only now being shown. */
export function scrollToFold(foldId: string): () => void {
  let attempts = 0;
  let timer: number | undefined;
  const find = () => {
    const target = document.getElementById(`${foldId}-section-body`)?.closest("section");
    if (target) target.scrollIntoView({ block: "start", behavior: "smooth" });
    else if (++attempts < FOLD_FIND_ATTEMPTS) timer = window.setTimeout(find, FOLD_FIND_INTERVAL_MS);
  };
  timer = window.setTimeout(find, 0);
  return () => window.clearTimeout(timer);
}
