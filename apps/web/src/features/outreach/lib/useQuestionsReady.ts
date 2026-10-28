import { useQuery } from "@tanstack/react-query";
import * as notetakerApi from "../api/notetakerApi";

/**
 * How many questions a call's plan asks once drafted — the plan the live copilot follows, saved or not — or null while
 * none is.
 */
export function useQuestionsReady(
  projectId: string,
  candidateId: string,
  meetingId: string,
  enabled = true,
): number | null {
  const plan = useQuery({
    queryKey: notetakerApi.INTERVIEW_PLAN_KEY(projectId, candidateId, meetingId),
    queryFn: ({ signal }) => notetakerApi.getInterviewPlan(projectId, candidateId, meetingId, signal),
    enabled,
  });
  if (!enabled || !plan.data?.drafted) return null;
  return plan.data.questions.filter((question) => question.included).length;
}
