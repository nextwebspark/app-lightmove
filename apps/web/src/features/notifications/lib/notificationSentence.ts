import { formatInstantDateTime, formatInstantTime } from "../../../lib/format";
import { noteWordsOf } from "../../tasks/lib/linkedNote";
import type { NotificationResponse } from "../api/types";
import { dayKeyOf } from "../../../lib/datetime";

export interface SentencePart {
  text: string;
  /** A name — who, which task, which executive, which position — drawn bold. */
  strong?: boolean;
  /** A trailing aside, drawn in the row's muted colour. */
  muted?: boolean;
}

export type SubLineTone = "task" | "muted";

/** What a bell row says. A note is named, never quoted: the row is not the note's reader. */
export interface NotificationSentence {
  parts: SentencePart[];
  /** The position it happened on, said after the sentence; null where the sentence names it already. */
  where: string | null;
  sub: { text: string; tone: SubLineTone } | null;
}

export function sentenceOf(notification: NotificationResponse, now: Date = new Date()): NotificationSentence {
  const actor = actorNameOf(notification);
  const person = notification.personName ?? "an executive";
  const task = notification.taskTitle ?? "a task";
  const position = notification.projectTitle ?? "a position";
  const where = notification.projectTitle;
  const strong = (text: string): SentencePart => ({ text, strong: true });
  const plain = (text: string): SentencePart => ({ text });
  const onYourTasks = notification.taskId ? taskSubLine(`${task} is on your tasks`) : null;

  switch (notification.kind) {
    case "task_assigned": {
      const due = formatInstantDateTime(notification.dueAt);
      const sub = [notification.note && `From ${noteWordsOf(notification.note.kind)}`, due && `Due ${due}`]
        .filter(Boolean)
        .join(" · ");
      return {
        parts: [strong(actor), plain(" assigned you "), strong(task), plain(" on "), strong(person)],
        where,
        sub: sub ? { text: sub, tone: "muted" } : null,
      };
    }
    case "task_due_soon":
      return {
        parts: [strong(task), plain(" on "), strong(person), plain(` is due ${dueClauseOf(notification.dueAt, now)}`)],
        where,
        sub: null,
      };
    case "task_overdue":
      return {
        parts: [
          strong(task),
          plain(" on "),
          strong(person),
          plain(" is overdue"),
          ...(notification.dueAt ? [{ text: ` · due ${formatInstantDateTime(notification.dueAt)}`, muted: true }] : []),
        ],
        where,
        sub: null,
      };
    case "mentioned":
      return {
        parts: [
          strong(actor),
          plain(` mentioned you in ${notification.note ? noteWordsOf(notification.note.kind) : "a note"} on `),
          strong(person),
        ],
        where,
        sub: null,
      };
    case "outreach_replied":
      if (notification.count > 1) {
        const names = namesLine(notification.names, notification.count);
        return {
          parts: [
            strong(`${notification.count} replies`),
            plain(" on "),
            strong(position),
            ...(names ? [plain(` — ${names}`)] : []),
          ],
          where: null,
          sub: onYourTasks,
        };
      }
      return { parts: [strong(person), plain(" replied")], where, sub: onYourTasks };
    case "outreach_bounced":
      return { parts: [plain("An email to "), strong(person), plain(" bounced")], where, sub: onYourTasks };
    case "call_booked": {
      const due = formatInstantDateTime(notification.dueAt);
      return {
        parts: [strong(person), plain(" booked a call")],
        where,
        sub: notification.taskId ? taskSubLine(due ? `Debrief is on your tasks · ${due}` : "Debrief is on your tasks") : null,
      };
    }
    case "added_to_team":
      return { parts: [strong(actor), plain(" added you to "), strong(position)], where: null, sub: null };
    case "tasks_moved":
      return {
        parts: [
          strong(notification.count === 1 ? "1 task" : `${notification.count} tasks`),
          plain(" moved to you from "),
          strong(actor),
        ],
        where,
        sub: null,
      };
  }
}

/** The sentence as one line of text, its position included: what a screen reader says for the row. */
export function sentenceTextOf(sentence: NotificationSentence): string {
  const body = sentence.parts.map((part) => part.text).join("");
  return sentence.where ? `${body} · ${sentence.where}` : body;
}

/**
 * A grouped row's people, newest first: "Karim Nassar, Rania Odeh and 1 other". Two are named; the rest of
 * `count` is counted, so a person who wrote twice is still one name and the total still adds up.
 */
export function namesLine(names: readonly string[], count: number): string {
  const named = names.slice(0, 2);
  if (named.length === 0) return "";
  const others = Math.max(0, count - named.length);
  if (others === 0) return named.length === 1 ? named[0] : `${named[0]} and ${named[1]}`;
  return `${named.join(", ")} and ${others} ${others === 1 ? "other" : "others"}`;
}

function actorNameOf(notification: NotificationResponse): string {
  if (!notification.actor) return "Uncava";
  return notification.actor.fullName ?? "A teammate";
}

function taskSubLine(text: string): NotificationSentence["sub"] {
  return { text, tone: "task" };
}

/** "at 16:00" on the day itself, otherwise the whole "15 Oct 2026, 09:00". */
function dueClauseOf(dueAt: string | null, now: Date): string {
  if (!dueAt) return "soon";
  const sameDay = dayKeyOf(new Date(dueAt)) === dayKeyOf(now);
  return sameDay ? `at ${formatInstantTime(dueAt)}` : (formatInstantDateTime(dueAt) ?? "soon");
}
