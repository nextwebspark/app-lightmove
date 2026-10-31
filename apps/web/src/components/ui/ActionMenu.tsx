import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
import { Icon, ICONS } from "../layout/Icon";
import { Popover } from "./Popover";

/** A row's ⋯: the row stays just the item, and what can be done to it opens here. */
export function ActionMenu({
  label,
  width = 240,
  children,
}: {
  /** Names both the trigger and the menu, e.g. "Engaged options". */
  label: string;
  width?: number;
  children: (close: () => void) => ReactNode;
}) {
  return (
    <Popover
      label={label}
      title="Options"
      align="right"
      placement="auto"
      width={width}
      hasPopup="menu"
      triggerClassName="grid size-[26px] flex-none place-items-center rounded-u-control border border-transparent text-u-text3 transition-colors hover:border-u-border hover:bg-u-raised hover:text-u-text focus-visible:outline-2 focus-visible:outline-u-accent aria-expanded:border-u-border aria-expanded:bg-u-raised aria-expanded:text-u-text"
      trigger={() => <Icon d={ICONS.more} size={14} />}
    >
      {(close) => (
        <div role="menu" aria-label={label} className="flex flex-col">
          {children(close)}
        </div>
      )}
    </Popover>
  );
}

export function MenuItem({
  icon,
  tone,
  note,
  disabled,
  onSelect,
  children,
}: {
  icon: string;
  tone?: "danger";
  /** A second, quieter line: what choosing it does, or why it cannot be chosen. */
  note?: string;
  disabled?: boolean;
  onSelect: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      disabled={disabled}
      onClick={onSelect}
      className={cn(
        "flex w-full items-start gap-2.5 rounded-u-control px-2 py-1.5 text-start text-note enabled:hover:bg-u-raised disabled:cursor-not-allowed disabled:opacity-50",
        tone === "danger" ? "text-u-offlimits" : "text-u-text",
      )}
    >
      <Icon d={icon} size={13} className="mt-[3px] flex-none opacity-80" />
      {note ? (
        <span className="flex flex-col">
          {children}
          <span className="text-meta text-u-text3">{note}</span>
        </span>
      ) : (
        children
      )}
    </button>
  );
}

export function MenuSeparator() {
  return <div role="separator" className="my-1 h-px bg-u-border" />;
}
