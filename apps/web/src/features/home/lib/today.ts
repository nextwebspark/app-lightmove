import type { TaskResponse } from "../../tasks/api/types";
import type { MyMeeting } from "../api/homeApi";

/** One entry of Today: a meeting on the caller's calendar, or a call or meeting task set for today. */
export interface TodayEvent {
  key: string;
  startsAt: string;
  /** Null for a task, which has a time but no length. */
  minutes: number | null;
  title: string;
  personId: string;
  personName: string | null;
  projectId: string | null;
  candidateId: string | null;
  positionTitle: string | null;
  clientName: string | null;
  videoProvider: string | null;
  joinUrl: string | null;
  /** The task's kind label ("Call", "Meeting") where the entry is a task. */
  taskLabel: string | null;
}

const SAME_CALL_WITHIN_MS = 60_000;

/**
 * Today's calendar and today's call and meeting tasks, soonest first. A task for the same person at the same time as
 * a meeting is that meeting, so it is drawn once, as the meeting.
 */
export function todayEventsOf(
  meetings: readonly MyMeeting[],
  agenda: readonly TaskResponse[],
  clientOf: (projectId: string) => string | undefined,
  labelOf: (task: TaskResponse) => string,
): TodayEvent[] {
  const fromMeetings = meetings.map((meeting): TodayEvent => ({
    key: `m:${meeting.id}`,
    startsAt: meeting.startsAt,
    minutes: Math.round((new Date(meeting.endsAt).getTime() - new Date(meeting.startsAt).getTime()) / 60_000),
    title: meeting.title?.trim() || "Meeting",
    personId: meeting.personId,
    personName: meeting.personName,
    projectId: meeting.projectId,
    candidateId: meeting.candidateId,
    positionTitle: meeting.positionTitle,
    clientName: meeting.clientName,
    videoProvider: meeting.videoProvider,
    joinUrl: meeting.joinUrl,
    taskLabel: null,
  }));
  const isMeeting = (task: TaskResponse) =>
    meetings.some(
      (meeting) =>
        meeting.personId === task.personId &&
        Math.abs(new Date(meeting.startsAt).getTime() - new Date(task.dueAt ?? 0).getTime()) < SAME_CALL_WITHIN_MS,
    );
  const fromTasks = agenda
    .filter((task) => task.dueAt !== null && !isMeeting(task))
    .map((task): TodayEvent => ({
      key: `t:${task.id}`,
      startsAt: task.dueAt as string,
      minutes: null,
      title: task.title,
      personId: task.personId,
      personName: task.personName,
      projectId: task.projectId,
      candidateId: task.candidateId,
      positionTitle: task.projectTitle,
      clientName: clientOf(task.projectId) ?? null,
      videoProvider: null,
      joinUrl: null,
      taskLabel: labelOf(task),
    }));
  return [...fromMeetings, ...fromTasks].sort(
    (left, right) => new Date(left.startsAt).getTime() - new Date(right.startsAt).getTime(),
  );
}

/** Whether an entry is behind the caller: a meeting once it has ended, so one under way is still the current one. */
export function isOverAt(event: TodayEvent, now: Date): boolean {
  const ends = new Date(event.startsAt).getTime() + (event.minutes ?? 0) * 60_000;
  return now.getTime() >= ends;
}

/** 30 → "30 min", 60 → "1 h", 90 → "1 h 30 min". */
export function lengthOf(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}
