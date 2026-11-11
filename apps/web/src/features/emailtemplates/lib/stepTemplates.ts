import type { SequenceStep } from "../../outreach/api/sequenceApi";
import { SIGNATURE_TOKEN, tokenUsesIn, withoutToken } from "../../outreach/lib/sequenceTokens";
import type { EmailTemplate } from "../api/emailTemplatesApi";

/** A sequence email's own limit (`SequenceStepRequest`), half a template's. */
export const STEP_BODY_LIMIT = 5000;

const SIGNATURE = "signature";

/** Whether any step places the signature: a step placing none is sent unsigned, and Start draws none for such a run. */
export function sequenceSigns(steps: readonly SequenceStep[]): boolean {
  return steps.some((step) => tokenUsesIn(step.body).some((use) => use.name === SIGNATURE));
}

/**
 * A template copied into a step: its words as plain text (a step has no formatting), its subject into the first email
 * alone. Where the sequence signs, the copy closes with exactly one signature — the one chosen at Start — whatever the
 * step held before, so a step cleared and then filled from a template is never left unsigned.
 */
export function stepFromTemplate(
  template: EmailTemplate,
  step: SequenceStep,
  isFirstStep: boolean,
  signs: boolean,
): Pick<SequenceStep, "subject" | "body"> {
  const words = withoutToken(template.plainBody, SIGNATURE).trim();
  return {
    body: signs ? `${words}\n\n${SIGNATURE_TOKEN}` : words,
    subject: isFirstStep && template.subject ? template.subject : step.subject,
  };
}

/** Why a template cannot be copied into a step, or null where it can. */
export function stepRefusalOf(template: EmailTemplate, step: SequenceStep, signs: boolean): string | null {
  const length = stepFromTemplate(template, step, false, signs).body.length;
  return length > STEP_BODY_LIMIT
    ? `${length.toLocaleString()} characters as plain text: a sequence email holds ${STEP_BODY_LIMIT.toLocaleString()}.`
    : null;
}

/** Whether replacing the step loses anything a consultant typed. */
export function stepHasText(step: SequenceStep, isFirstStep: boolean): boolean {
  return templateWordsOfStep(step) !== "" || (isFirstStep && Boolean(step.subject?.trim()));
}

/** A step's words for a template: the signature it places is left out, since an email's comes from Include signature. */
export function templateWordsOfStep(step: SequenceStep): string {
  return withoutToken(step.body, SIGNATURE).trim();
}

/** Why a step cannot become a template, or null where it can. */
export function templateRefusalOf(step: SequenceStep): string | null {
  if (tokenUsesIn(step.body).some((use) => use.name === "opener")) {
    return "This step uses {{opener}}, the line AI writes for each person when the sequence starts. A template can't fill it: take it out of the step to save the step as a template.";
  }
  return templateWordsOfStep(step) === "" ? "This step has no words of its own to save." : null;
}
