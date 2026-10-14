import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import { teammateLabel, type TeammateOption } from "../lib/team";
import { RolePill } from "./TaskBits";

/**
 * Who a task goes to: the position's team, me pinned first, each drawn with their avatar and role. Never a
 * client seat — the caller's options are already the staff alone. `footer` is the line under the list.
 */
export function AssigneePicker({
  label,
  options,
  value,
  onChange,
  disabled = false,
  compact = false,
  footer,
}: {
  label: string;
  options: readonly TeammateOption[];
  value: string;
  onChange: (userId: string) => void;
  disabled?: boolean;
  compact?: boolean;
  footer?: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const listId = useId();
  const labelId = useId();
  const chosen = options.find((option) => option.userId === value) ?? null;
  useEscapeKey(open, () => setOpen(false));

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutside);
    box.current?.querySelector<HTMLElement>('[aria-selected="true"]')?.focus();
    return () => document.removeEventListener("mousedown", closeOnOutside);
  }, [open]);

  return (
    <div ref={box} className="relative">
      <span id={labelId} className="sr-only">
        {label}
      </span>
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-labelledby={`${labelId} ${labelId}-value`}
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
        className={cn(
          "flex w-full items-center gap-2 rounded-u-control border border-u-border-strong bg-u-raised px-2.5 text-start transition focus:border-u-accent disabled:opacity-60",
          compact ? "h-9" : "h-10",
        )}
      >
        {chosen && <Avatar id={chosen.userId} name={chosen.fullName} src={chosen.avatarUrl} size={compact ? "xs" : "sm"} />}
        <span id={`${labelId}-value`} className={cn("min-w-0 flex-1 truncate font-medium text-u-text", compact ? "text-note" : "text-body")}>
          {chosen ? teammateLabel(chosen) : "Choose someone"}
        </span>
        {chosen && !compact && <RolePill role={chosen.role} />}
        <Icon d={ICONS.chevronDown} size={14} className="flex-none text-u-text3" />
      </button>
      {open && (
        <div className="absolute inset-x-0 top-full z-20 mt-1 rounded-u-chip border border-u-border-strong bg-u-surface p-1 shadow-u-e3">
          <ul id={listId} role="listbox" aria-label={label} className="max-h-64 overflow-y-auto">
            {options.map((option) => {
              const selected = option.userId === value;
              return (
                <li key={option.userId} role="presentation" className={cn(option.isMe && "mb-0.5")}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={selected}
                    onClick={() => {
                      onChange(option.userId);
                      setOpen(false);
                    }}
                    className={cn(
                      "flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start hover:bg-u-raised focus:bg-u-raised focus:outline-none",
                      selected && "bg-u-raised",
                    )}
                  >
                    <Avatar id={option.userId} name={option.fullName} src={option.avatarUrl} size="md" />
                    <span className="min-w-0 flex-1 truncate text-body font-medium text-u-text">{teammateLabel(option)}</span>
                    <RolePill role={option.role} />
                    {selected && <Icon d={ICONS.check} size={14} className="flex-none text-u-accent" />}
                  </button>
                </li>
              );
            })}
          </ul>
          {footer && (
            <div className="mt-1 flex items-center justify-between gap-2 border-t border-u-border px-2 pb-1 pt-2 text-meta text-u-text3">
              {footer}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
