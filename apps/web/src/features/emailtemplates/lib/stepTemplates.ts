import type { SequenceStep } from "../../outreach/api/sequenceApi";
import { SIGNATURE_TOKEN, tokenUsesIn } from "../../outreach/lib/sequenceTokens";
import type { EmailTemplate } from "../api/emailTemplatesApi";

/** A sequence email's own limit (`SequenceStepRequest`), half a template's. */
export const STEP_BODY_LIMIT = 5000;

const SIGNATURE_TOKENS = /\n*[ \t]*\{\{\s*signature\s*\}\}[ \t]*/g;

/**
 * A template copied into a step: its words as plain text (a step has no formatting), its subject into the first email
 * alone. Where the step placed the signature, it still closes it: a step placing none is sent unsigned.
 */
export function stepFromTemplate(
  template: EmailTemplate,
  step: SequenceStep,
  isFirstStep: boolean,
): Pick<SequenceStep, "subject" | "body"> {
  const placesSignature = tokenUsesIn(step.body).some((use) => use.name === "signature");
  const body = placesSignature ? `${template.plainBody}\n\n${SIGNATURE_TOKEN}` : template.plainBody;
  return { body, subject: isFirstStep && template.subject ? template.subject : step.subject };
}

/** Why a template cannot be copied into a step, or null where it can. */
export function stepRefusalOf(template: EmailTemplate, step: SequenceStep): string | null {
  const length = stepFromTemplate(template, step, false).body.length;
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
  return step.body.replace(SIGNATURE_TOKENS, "").trim();
}

/** Why a step cannot become a template, or null where it can. */
export function templateRefusalOf(step: SequenceStep): string | null {
  if (tokenUsesIn(step.body).some((use) => use.name === "opener")) {
    return "This step uses {{opener}}, the line AI writes for each person when the sequence starts. A template can't fill it: take it out of the step to save the step as a template.";
  }
  return templateWordsOfStep(step) === "" ? "This step has no words of its own to save." : null;
}
