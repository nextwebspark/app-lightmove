import type { TaskKind } from "../api/types";

export interface TaskKindOption {
  value: TaskKind;
  label: string;
  icon: string;
}

/** The five kinds a task is, in the order the Next action modal offers them, each with the mockup's glyph. */
export const TASK_KINDS: readonly TaskKindOption[] = [
  {
    value: "call",
    label: "Call",
    icon: "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2Z",
  },
  { value: "email", label: "Email", icon: "M4 4h16v16H4zM22 6l-10 7L2 6" },
  {
    value: "meeting",
    label: "Meeting",
    icon: "M8 2v4M16 2v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z",
  },
  { value: "follow_up", label: "Follow-up", icon: "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5" },
  {
    value: "todo",
    label: "To-do",
    icon: "m9 11 3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
  },
];

export function taskKindOf(kind: TaskKind): TaskKindOption {
  return TASK_KINDS.find((option) => option.value === kind) ?? TASK_KINDS[TASK_KINDS.length - 1];
}

/** The bell the modal draws beside "is told in the app". */
export const BELL_ICON = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0";

/** Who filed a rule's task, said on its Auto pill. */
export const AUTO_TITLE = "Filed by Uncava from outreach";
