// Ported from apps/web/src/features/notes/components/MentionPickerList.tsx; keep the two in step.
import { useEffect, useReducer } from "react";
import { createPortal } from "react-dom";
import { EDGE_GAP, PANEL_GAP, verticalPlacement } from "../../lib/anchoredPlacement";
import { Avatar } from "../../components/Avatar";
import { cn } from "../../lib/cn";
import type { Mention } from "../lib/mentions";
import type { TeammateOption } from "../lib/teammates";

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

const PICKER_WIDTH = 300;

/**
 * The mention picker (`Position.dc.html`, "Mention picker"): the team, one row each, the active one raised.
 *
 * <p>Portalled and placed at the `@` it answers: drawn inside the editor, the note modal's scrolling body and the
 * Expand view's footer clipped it out of sight.
 */
export function MentionPickerList({
  anchor,
  options,
  active,
  teammates,
  onActive,
  onPick,
}: {
  /** The box it opens against — the `@` being typed — read again whenever anything behind it scrolls. */
  anchor: () => DOMRect | null;
  options: readonly MentionPickerOption[];
  active: number;
  teammates: readonly TeammateOption[];
  onActive: (index: number) => void;
  onPick: (mention: Mention) => void;
}) {
  const [, remeasure] = useReducer((count: number) => count + 1, 0);
  useEffect(() => {
    // Capture: what scrolls is the modal's body or the drawer, never the window.
    window.addEventListener("scroll", remeasure, true);
    window.addEventListener("resize", remeasure);
    return () => {
      window.removeEventListener("scroll", remeasure, true);
      window.removeEventListener("resize", remeasure);
    };
  }, []);

  const box = anchor();
  if (!box) return null;
  const others = teammates.filter((teammate) => !teammate.isMe);
  const placed = verticalPlacement(box, "auto");
  return createPortal(
    <div
      onMouseDown={(event) => event.preventDefault()}
      style={{
        ...placed,
        maxHeight: "maxHeight" in placed ? placed.maxHeight : window.innerHeight - box.bottom - PANEL_GAP - EDGE_GAP,
        left: Math.max(EDGE_GAP, Math.min(box.left, window.innerWidth - PICKER_WIDTH - EDGE_GAP)),
        width: PICKER_WIDTH,
        maxWidth: `calc(100vw - ${EDGE_GAP * 2}px)`,
      }}
      // Above the modal (100) the note is often typed in, below the toast (120), as `Popover` sits.
      className="fixed z-[110] flex flex-col rounded-u-chip border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
    >
      <ul role="listbox" aria-label="Mention someone on this position's team" className="min-h-0 max-h-64 overflow-y-auto">
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
    </div>,
    document.body,
  );
}

function RolePill({ role }: { role: TeammateOption["role"] }) {
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
