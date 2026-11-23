import { PromptPill } from "../../prompts/components/PromptPill";
import type { EvalRunStatus, EvalRunVerdict } from "../api/types";

/**
 * What a finished run found, in the two words the console uses for it. A run that finished with nothing it
 * measured counting reads No result; one that never finished shows nothing.
 */
export function VerdictPill({ verdict, status }: { verdict: EvalRunVerdict | null; status: EvalRunStatus }) {
  if (verdict === "PASS") return <PromptPill tone="live">No worse</PromptPill>;
  if (verdict === "FAIL") return <PromptPill tone="danger">Regressed</PromptPill>;
  return status === "FINISHED" ? <PromptPill tone="plain">No result</PromptPill> : null;
}
