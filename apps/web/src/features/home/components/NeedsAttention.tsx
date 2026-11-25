import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { buttonClassName, LinesSkeleton, SegmentedControl } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { formatInstantTime, formatShortDate } from "../../../lib/datetime/format";
import { dayKeyOf, daysBetween } from "../../../lib/datetime/zone";
import { formatTimeAgoShort } from "../../../lib/format";
import type { ConversationScope } from "../../outreach/api/conversationApi";
import { taskKindOf } from "../../tasks/lib/taskKinds";
import type { AttentionLine, AttentionReason } from "../lib/needsYou";
import { recordPath } from "../lib/taskPath";
import { CardEmpty, CardNudge, CountBadge, HomeCard } from "./HomeCard";

/** Why replies may be missing from the list: no mailbox connected, or one that needs connecting again. */
export type MailboxGap = "missing" | "broken" | null;

const VISIBLE = 6;

const TONE: Record<AttentionReason, string> = {
  overdue: "bg-u-signal-tint text-u-signal",
  reply: "bg-u-accent-tint text-u-accent",
  delivery: "bg-u-offlimits-tint text-u-offlimits",
  today: "bg-u-raised text-u-text2",
  later: "bg-u-raised text-u-text2",
};

/**
 * Needs your attention: replies, delivery problems and every task owed, in one list ordered by how pressing each is.
 * The first line's action is the page's primary one.
 */
export function NeedsAttention({
  lines,
  comingUp,
  moreOutreach,
  moreTasks,
  clientOf,
  myUserId,
  pending,
  refused,
  scope,
  onScopeChange,
  showScope,
  mailboxGap,
  onAnyPosition,
  now,
}: {
  lines: AttentionLine[];
  /** Tasks not due yet, left to My tasks and only counted here. */
  comingUp: number;
  /** More outreach rows wait than the server sent. */
  moreOutreach: boolean;
  /** More of the caller's tasks are due than one read holds. */
  moreTasks: boolean;
  clientOf: (projectId: string) => string | undefined;
  myUserId: string | undefined;
  pending: boolean;
  refused: boolean;
  scope: ConversationScope;
  onScopeChange: (scope: ConversationScope) => void;
  showScope: boolean;
  mailboxGap: MailboxGap;
  /** Whether the caller holds a seat on any live position: with none, nothing can be owed yet. */
  onAnyPosition: boolean;
  now: Date;
}) {
  const [expanded, setExpanded] = useState(false);
  const shown = expanded ? lines : lines.slice(0, VISIBLE);
  const hidden = lines.length - shown.length;
  const count = `${lines.length}${moreOutreach || moreTasks ? "+" : ""}`;

  return (
    <HomeCard
      title="Needs your attention"
      badge={!pending && !refused && lines.length > 0 ? <CountBadge count={count} /> : undefined}
      subtitle="Most urgent first"
      aside={
        showScope ? (
          <SegmentedControl
            label="Whose items"
            value={scope}
            onChange={onScopeChange}
            options={[
              { value: "mine", label: "Mine" },
              { value: "team", label: "Team" },
            ]}
          />
        ) : undefined
      }
      footer={
        refused || pending || lines.length === 0 ? undefined : (
        <>
          <span className="text-u-text3">
            {scope === "team"
              ? "You're seeing your actions and what waits across the positions you lead"
              : "You're seeing actions assigned to you"}
          </span>
          {moreOutreach && <span className="text-u-text3">More wait on each position's Outreach page.</span>}
          {moreTasks && !moreOutreach && <span className="text-u-text3">More are on My tasks.</span>}
          {(expanded || hidden > 0) && (
            <button type="button" onClick={() => setExpanded(!expanded)} className="font-medium text-u-accent hover:underline">
              {expanded ? "Show fewer" : `Show ${hidden} more`}
            </button>
          )}
          <span className="ml-auto flex items-center gap-3">
            {comingUp > 0 && <span className="text-u-text3">{comingUp} coming up</span>}
            <Link to="/tasks" className="font-medium text-u-accent hover:underline">
              All my tasks
            </Link>
          </span>
        </>
        )
      }
    >
      {!pending && mailboxGap && (
        <CardNudge
          icon={ICONS.mail}
          action={
            <Link to="/settings/integrations" className="font-medium text-u-accent hover:underline">
              {mailboxGap === "missing" ? "Connect mailbox" : "Reconnect"}
            </Link>
          }
        >
          {mailboxGap === "missing"
            ? "Connect your mailbox to see replies and delivery problems here."
            : "Your mailbox needs reconnecting, so new replies may be missing."}
        </CardNudge>
      )}
      {pending ? (
        <div className="px-5 pb-4">
          <LinesSkeleton lines={4} />
        </div>
      ) : refused ? (
        <CardEmpty
          icon={ICONS.lock}
          title="Couldn't load what needs you"
          body="Reload the page. Your tasks and outreach are still on each position."
        />
      ) : lines.length === 0 ? (
        scope === "team" ? (
          <CardEmpty icon={ICONS.check} title="Nothing waits on your team" body="Replies and tasks on the positions you lead land here." />
        ) : onAnyPosition ? (
          <CardEmpty
            icon={ICONS.check}
            title="You're all caught up"
            body={
              comingUp > 0
                ? `Nothing is due today. ${comingUp === 1 ? "1 task is" : `${comingUp} tasks are`} coming up on My tasks.`
                : "Replies, follow-ups and tasks land here as they come in."
            }
            action={
              <Link to="/tasks" className={buttonClassName("secondary", "px-3 py-1.5 text-note")}>
                Open My tasks
              </Link>
            }
          />
        ) : (
          <CardEmpty
            icon={ICONS.checklist}
            title="Nothing needs you yet"
            body="Once you're on a position, replies from executives, follow-ups and your tasks gather here, most urgent first."
          />
        )
      ) : (
        <ul aria-label="Needs your attention">
          {shown.map((line, index) => (
            <AttentionRow
              key={line.key}
              line={line}
              primary={index === 0}
              clientName={clientOf(line.projectId)}
              scope={scope}
              showAssignee={scope === "team" && line.assignee !== null && line.assignee.userId !== myUserId}
              now={now}
            />
          ))}
        </ul>
      )}
    </HomeCard>
  );
}

function AttentionRow({
  line,
  primary,
  clientName,
  scope,
  showAssignee,
  now,
}: {
  line: AttentionLine;
  primary: boolean;
  clientName: string | undefined;
  scope: ConversationScope;
  showAssignee: boolean;
  now: Date;
}) {
  const wordedFromPerson = line.taskTitle === null;
  const where = [wordedFromPerson ? null : line.personName, line.positionTitle, clientName, showAssignee ? line.assignee?.fullName : null]
    .filter(Boolean)
    .join(" · ");
  const conversation = `/projects/${line.projectId}/outreach?view=needs&who=${scope}&candidate=${line.candidateId}`;
  const to = line.group ? conversation : recordPath(line.projectId, line.candidateId, line.personId);
  const title = titleOf(line);
  const chip = chipOf(line, now);

  return (
    <li className="relative grid grid-cols-[32px_minmax(0,1fr)] items-center gap-x-3 gap-y-2 border-t border-u-border px-5 py-3 transition-colors first:border-t-0 hover:bg-u-raised sm:grid-cols-[32px_minmax(0,1fr)_auto_auto]">
      <span aria-hidden="true" className={cn("grid size-8 place-items-center rounded-u-chip", TONE[line.reason])}>
        <Icon d={iconOf(line)} size={15} />
      </span>
      <span className="min-w-0">
        <Link
          to={recordPath(line.projectId, line.candidateId, line.personId, line.group ? "outreach" : undefined)}
          className="line-clamp-2 text-body font-medium text-u-text after:absolute after:inset-0 after:content-[''] sm:line-clamp-none sm:block sm:truncate"
        >
          {title}
        </Link>
        {where && <span className="block truncate text-meta text-u-text3">{where}</span>}
      </span>
      <div className="col-start-2 flex items-center gap-2 sm:contents">
        {chip ? (
          <span
            className={cn(
              "whitespace-nowrap rounded-u-control px-2 py-0.5 font-mono text-meta font-medium sm:justify-self-end",
              TONE[line.reason],
            )}
          >
            {chip}
          </span>
        ) : (
          <span className="hidden sm:block" />
        )}
        <Link
          to={to}
          aria-label={`${actionOf(line)}: ${title}`}
          className={buttonClassName(primary ? "primary" : "secondary", "relative z-10 ml-auto px-3 py-1.5 text-note sm:ml-0")}
        >
          {actionOf(line)}
          <Icon d={ICONS.arrowRight} size={13} />
        </Link>
      </div>
    </li>
  );
}

function titleOf(line: AttentionLine): string {
  if (line.taskTitle) return line.taskTitle;
  if (line.reason === "reply") return `${line.personName} replied to your outreach`;
  if (line.reason === "delivery") return `Outreach to ${line.personName} hit a delivery problem`;
  if (line.group === "FOLLOW_UP") return `Follow up with ${line.personName}`;
  return `Get ready to email ${line.personName}`;
}

function iconOf(line: AttentionLine): string {
  if (line.reason === "reply") return ICONS.reply;
  if (line.reason === "delivery") return ICONS.warning;
  if (line.reason === "overdue") return ICONS.clock;
  if (line.taskKind) return taskKindOf(line.taskKind).icon;
  return ICONS.mail;
}

function actionOf(line: AttentionLine): string {
  if (line.reason === "reply") return "Reply";
  if (line.reason === "delivery") return "Resolve";
  if (line.taskKind === "follow_up" || line.group === "FOLLOW_UP") return "Follow up";
  if (line.taskKind === "call") return "Call";
  return "Open";
}

function chipOf(line: AttentionLine, now: Date): string | null {
  if (line.reason === "delivery") return "Delivery problem";
  if (!line.at) return null;
  const at = new Date(line.at);
  if (line.reason === "reply") return formatTimeAgoShort(line.at, now);
  const days = daysBetween(dayKeyOf(at), dayKeyOf(now));
  if (line.reason === "overdue") return days > 0 ? `${days}d overdue` : "Overdue";
  if (line.reason === "today") return `Due ${formatInstantTime(at)}`;
  return days === -1 ? "Due tomorrow" : `Due ${formatShortDate(at)}`;
}
