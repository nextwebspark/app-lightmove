import { positionPipelinePath } from "../../pipeline/lib/pipelinePath";
import type { TaskResponse } from "../../tasks/api/types";

/** Where a task opens: its executive on the position's board, or the board when it names none. */
export function taskPath(task: Pick<TaskResponse, "projectId" | "candidateId">): string {
  return positionPipelinePath(task.projectId, task.candidateId ? { candidate: task.candidateId } : {});
}
