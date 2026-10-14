import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { describeDue, type DueTone } from "../../../lib/format";
import { AUTO_TITLE } from "../lib/taskKinds";
import type { TeammateOption } from "../lib/team";

const TONE_TEXT: Record<Exclude<DueTone, "plain">, string> = {
  overdue: "text-u-offlimits",
  today: "text-u-signal",
};

/** A due moment as a line says it — red when overdue, amber today, `plainClassName` after. */
export function DueText({
  dueAt,
  withIcon = false,
  plainClassName = "text-u-text3",
  className,
}: {
  dueAt: string | null;
  withIcon?: boolean;
  plainClassName?: string;
  className?: string;
}) {
  const due = describeDue(dueAt);
  if (!due) return null;
  return (
    <span
      data-tone={due.tone}
      className={cn(
        "inline-flex items-center gap-1 whitespace-nowrap",
        due.tone === "plain" ? plainClassName : TONE_TEXT[due.tone],
        className,
      )}
    >
      {withIcon && <Icon d={ICONS.clock} size={12} className="flex-none" />}
      {due.label}
    </span>
  );
}

const PILL_TONE: Record<DueTone, string> = {
  overdue: "bg-u-offlimits-tint text-u-offlimits",
  today: "bg-u-signal-tint text-u-signal",
  plain: "bg-u-raised text-u-text2",
};

/** My tasks' due chip: the same words as {@link DueText}, on a tinted pill. */
export function DuePill({ dueAt }: { dueAt: string | null }) {
  const due = describeDue(dueAt);
  if (!due) return null;
  return (
    <span
      data-tone={due.tone}
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 font-mono text-meta font-medium",
        PILL_TONE[due.tone],
      )}
    >
      <Icon d={due.tone === "overdue" ? ICONS.warning : ICONS.clock} size={11} className="flex-none" />
      {due.label}
    </span>
  );
}

/** A rule's task: filed by Uncava from an outreach event rather than by a person. */
export function AutoPill() {
  return (
    <span
      title={AUTO_TITLE}
      className="inline-flex flex-none items-center gap-0.5 rounded-full bg-u-inferred-tint px-1.5 py-px text-eyebrow font-semibold text-u-inferred"
    >
      <Icon d={ICONS.sparkle} size={9} />
      Auto
    </span>
  );
}

export function RolePill({ role }: { role: TeammateOption["role"] }) {
  if (!role) return null;
  return (
    <span
      className={cn(
        "flex-none rounded-full px-2 py-px text-eyebrow font-semibold",
        role === "Lead" ? "bg-u-accent-tint text-u-accent" : "bg-u-border text-u-text3",
      )}
    >
      {role}
    </span>
  );
}
