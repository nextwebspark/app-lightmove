import { useQuery } from "@tanstack/react-query";
import * as notetakerApi from "../api/notetakerApi";

/** How many questions a call's saved plan asks, or null while none is saved: its row and its panel say so alike. */
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
  if (!enabled || !plan.data?.savedAt) return null;
  return plan.data.questions.filter((question) => question.included).length;
}

export function questionsReadyLabelOf(count: number): string {
  return count === 1 ? "1 question ready" : `${count} questions ready`;
}
