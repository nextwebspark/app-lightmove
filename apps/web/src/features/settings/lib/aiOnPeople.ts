import type { AiControls, InferredBackground } from "../../workspace/api/types";

/** The two inferences whose off switch clears what they left. */
export type ClearingInference = "inferGender" | "inferNationality";

export const AI_SWITCHES: readonly { key: keyof AiControls; label: string; description: string }[] = [
  {
    key: "inferGender",
    label: "Infer gender",
    description: "Suggest a gender from a candidate's first name and pronouns when none is recorded.",
  },
  {
    key: "inferNationality",
    label: "Infer nationality",
    description: "Suggest a nationality group from a candidate's profile, with the evidence for and against.",
  },
  {
    key: "draftAssessments",
    label: "Draft assessments with AI",
    description: "Let Assess with AI draft scores and a report against the position's brief for a consultant to review.",
  },
  {
    key: "enrichAutomatically",
    label: "AI-enrich on capture",
    description: "Suggest missing background as soon as a candidate is captured or sourced, without anyone asking.",
  },
];

function candidates(count: number): string {
  return count === 1 ? "1 candidate" : `${count} candidates`;
}

/** The question before an off switch that clears, with the count once it is known. */
export function clearingConfirmation(
  inference: ClearingInference,
  held: InferredBackground | undefined,
): { title: string; body: string } {
  const isGender = inference === "inferGender";
  const what = isGender ? "gender" : "nationality";
  const title = `Turn off ${what} inference?`;
  const count = held ? (isGender ? held.genderValues : held.nationalityValues) : null;
  if (count === 0) {
    return { title, body: `No candidate holds a ${what} the AI suggested, so nothing is cleared.` };
  }
  const subject = count === null ? `every candidate it suggested one for` : candidates(count);
  const cleared = isGender
    ? "the gender the AI suggested and nobody confirmed"
    : "the nationality the AI suggested and nobody confirmed, and its reading of the evidence,";
  return {
    title,
    body: `This clears ${cleared} for ${subject}. ${isGender ? "Genders" : "Nationalities"} your team recorded stay.`,
  };
}
