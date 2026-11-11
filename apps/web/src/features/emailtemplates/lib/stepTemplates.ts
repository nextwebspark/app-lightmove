import type { SequenceStep } from "../../outreach/api/sequenceApi";
import { tokenUsesIn } from "../../outreach/lib/sequenceTokens";
import type { EmailTemplate } from "../api/emailTemplatesApi";

/** A sequence email's own limit (`SequenceStepRequest`), half a template's. */
export const STEP_BODY_LIMIT = 5000;

/**
 * A template copied into a step: its words as plain text (a step has no formatting), its subject into the first email
 * alone. Neither holds a signature: Include signature adds the sender's own when the sequence starts.
 */
export function stepFromTemplate(
  template: EmailTemplate,
  step: SequenceStep,
  isFirstStep: boolean,
): Pick<SequenceStep, "subject" | "body"> {
  return {
    body: template.plainBody.trim(),
    subject: isFirstStep && template.subject ? template.subject : step.subject,
  };
}

/** Why a template cannot be copied into a step, or null where it can. */
export function stepRefusalOf(template: EmailTemplate): string | null {
  const length = template.plainBody.trim().length;
  return length > STEP_BODY_LIMIT
    ? `${length.toLocaleString()} characters as plain text: a sequence email holds ${STEP_BODY_LIMIT.toLocaleString()}.`
    : null;
}

/** Whether replacing the step loses anything a consultant typed. */
export function stepHasText(step: SequenceStep, isFirstStep: boolean): boolean {
  return step.body.trim() !== "" || (isFirstStep && Boolean(step.subject?.trim()));
}

/** Why a step cannot become a template, or null where it can. */
export function templateRefusalOf(step: SequenceStep): string | null {
  if (tokenUsesIn(step.body).some((use) => use.name === "opener")) {
    return "This step uses {{opener}}, the line AI writes for each person when the sequence starts. A template can't fill it: take it out of the step to save the step as a template.";
  }
  return step.body.trim() === "" ? "This step has no words of its own to save." : null;
}
