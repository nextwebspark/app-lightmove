import * as tasksApi from "../api/tasksApi";
import type { TaskResponse } from "../api/types";
import { defaultDueOf, dueInstantOf } from "./due";

/** A follow-up the note composer files beside the note: a task of its own, linked to the note it came from. */
export interface FollowUpDraft {
  on: boolean;
  title: string;
  assigneeUserId: string;
  date: string;
}

const FOLLOW_UP_TIME = "09:00";

export function newFollowUpDraft(viewerId: string | null): FollowUpDraft {
  return { on: false, title: "", assigneeUserId: viewerId ?? "", date: defaultDueOf().date };
}

/** Off, or on with a title, an owner and a day it can be filed under. */
export function followUpReady(draft: FollowUpDraft): boolean {
  if (!draft.on) return true;
  return (
    draft.title.trim().length > 0 &&
    draft.assigneeUserId !== "" &&
    dueInstantOf({ date: draft.date, time: FOLLOW_UP_TIME }) !== null
  );
}

export function fileFollowUp(
  projectId: string,
  candidateId: string,
  noteId: string,
  draft: FollowUpDraft,
): Promise<TaskResponse> {
  return tasksApi.createTask(projectId, candidateId, {
    title: draft.title.trim(),
    kind: "follow_up",
    assigneeUserId: draft.assigneeUserId,
    dueAt: dueInstantOf({ date: draft.date, time: FOLLOW_UP_TIME })!,
    noteId,
  });
}
