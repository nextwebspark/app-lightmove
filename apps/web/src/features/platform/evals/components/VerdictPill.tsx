import { PromptPill } from "../../prompts/components/PromptPill";
import type { EvalRunVerdict } from "../api/types";

/** What a finished run found, in the two words the console uses for it. Nothing for a run with no result. */
export function VerdictPill({ verdict }: { verdict: EvalRunVerdict | null }) {
  if (verdict === "PASS") return <PromptPill tone="live">No worse</PromptPill>;
  if (verdict === "FAIL") return <PromptPill tone="danger">Regressed</PromptPill>;
  return null;
}
