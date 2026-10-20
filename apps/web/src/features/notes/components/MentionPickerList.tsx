import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { RolePill } from "../../tasks/components/TaskBits";
import type { Mention } from "../../tasks/lib/mentions";
import type { TeammateOption } from "../../tasks/lib/team";

export type MentionPickerOption = { mention: Mention; teammate: TeammateOption | null };

/** `@team` first when the query allows, then everyone on the team but me whose name holds it. */
export function mentionOptionsFor(teammates: readonly TeammateOption[], query: string): MentionPickerOption[] {
  const typed = query.toLowerCase();
  return [
    ...("team".startsWith(typed) ? [{ mention: { kind: "team" } as const, teammate: null }] : []),
    ...teammates
      .filter((teammate) => !teammate.isMe && teammate.fullName.toLowerCase().includes(typed))
      .map((teammate) => ({
        mention: { kind: "user" as const, userId: teammate.userId, name: teammate.fullName },
        teammate,
      })),
  ];
}

/** The mention picker (`Position.dc.html`, "Mention picker"): the team, one row each, the active one raised. */
export function MentionPickerList({
  options,
  active,
  teammates,
  onActive,
  onPick,
}: {
  options: readonly MentionPickerOption[];
  active: number;
  teammates: readonly TeammateOption[];
  onActive: (index: number) => void;
  onPick: (mention: Mention) => void;
}) {
  const others = teammates.filter((teammate) => !teammate.isMe);
  return (
    <div
      onMouseDown={(event) => event.preventDefault()}
      className="absolute left-0 top-full z-[110] mt-1 w-[300px] max-w-full rounded-u-chip border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
    >
      <ul role="listbox" aria-label="Mention someone on this position's team" className="max-h-64 overflow-y-auto">
        {options.map((option, index) => {
          const selected = index === active;
          const rowClass = cn(
            "flex cursor-pointer items-center gap-2.5 rounded-u-control px-2 py-1.5",
            selected && "bg-u-raised",
          );
          if (option.mention.kind === "team") {
            return (
              <li
                key="team"
                role="option"
                aria-selected={selected}
                onMouseEnter={() => onActive(index)}
                onClick={() => onPick(option.mention)}
                className={cn(rowClass, "mb-1 border-b border-u-border")}
              >
                <span className="flex flex-none">
                  {others.slice(0, 2).map((teammate, stacked) => (
                    <Avatar
                      key={teammate.userId}
                      id={teammate.userId}
                      name={teammate.fullName}
                      src={teammate.avatarUrl}
                      size="xs"
                      className={cn("border-2 border-u-surface", stacked > 0 && "-ms-2")}
                    />
                  ))}
                </span>
                <span className="flex-1 text-body font-semibold text-u-text">@team</span>
                <span className="text-meta text-u-text3">Everyone on this position</span>
              </li>
            );
          }
          const teammate = option.teammate!;
          return (
            <li
              key={teammate.userId}
              role="option"
              aria-selected={selected}
              onMouseEnter={() => onActive(index)}
              onClick={() => onPick(option.mention)}
              className={rowClass}
            >
              <Avatar id={teammate.userId} name={teammate.fullName} src={teammate.avatarUrl} size="sm" />
              <span className="min-w-0 flex-1 truncate text-body font-medium text-u-text">{teammate.fullName}</span>
              <RolePill role={teammate.role} />
            </li>
          );
        })}
      </ul>
      {options.length === 0 && (
        <p className="px-2 py-1.5 text-note text-u-text3">No one on this position's team matches.</p>
      )}
      <p className="mt-1 border-t border-u-border px-2 pb-1 pt-1.5 text-meta text-u-text3">
        Only this position's team. They're told who mentioned them, never what the note says.
      </p>
    </div>
  );
}
