import { useEffect, useState } from "react";
import { DRAFTING_STEPS } from "./searchProgress";

const STEP_EVERY_MS = 2_500;

/** The drafting line to show now: it moves through what the AI does and rests on the last until the draft lands. */
export function useDraftingStep(drafting: boolean): string {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (!drafting) {
      setStep(0);
      return;
    }
    const timer = window.setInterval(
      () => setStep((current) => Math.min(current + 1, DRAFTING_STEPS.length - 1)),
      STEP_EVERY_MS,
    );
    return () => window.clearInterval(timer);
  }, [drafting]);
  return DRAFTING_STEPS[step];
}
