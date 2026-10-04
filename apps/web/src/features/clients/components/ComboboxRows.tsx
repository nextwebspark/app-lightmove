import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";
import type { useComboboxList } from "../../../lib/useComboboxList";

type ComboboxList = ReturnType<typeof useComboboxList>;

/** The rows the business-unit and client fields share, so the two lists cannot drift apart. */
export function ComboboxOption({
  listId,
  index,
  list,
  className,
  children,
}: {
  listId: string;
  index: number;
  list: ComboboxList;
  className?: string;
  children: ReactNode;
}) {
  return (
    <li
      id={`${listId}-${index}`}
      role="option"
      aria-selected={index === list.active}
      onMouseDown={(event) => list.commitFromPointer(event, index)}
      onMouseEnter={() => list.setActive(index)}
      className={cn(
        "flex cursor-pointer items-center gap-2 px-3 py-[7px] font-sans text-body",
        index === list.active ? "bg-u-raised text-u-text" : "text-u-text2",
        className,
      )}
    >
      {children}
    </li>
  );
}

export function NewNameOption({
  listId,
  index,
  list,
  children,
}: {
  listId: string;
  index: number;
  list: ComboboxList;
  children: ReactNode;
}) {
  return (
    <ComboboxOption listId={listId} index={index} list={list} className={index > 0 ? "border-t border-u-border" : undefined}>
      <span aria-hidden="true" className="text-u-accent">
        ＋
      </span>
      <span className="truncate">{children}</span>
    </ComboboxOption>
  );
}

export function ComboboxGroupLabel({ children }: { children: ReactNode }) {
  return (
    <li
      role="presentation"
      className="border-t border-u-border px-3 pb-1 pt-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3 first:border-t-0"
    >
      {children}
    </li>
  );
}

export function ComboboxNote({ tone = "muted", children }: { tone?: "muted" | "error"; children: ReactNode }) {
  return (
    <li
      role="presentation"
      className={cn(
        "px-3 py-2 font-mono text-[11.5px]",
        tone === "error" ? "text-u-offlimits" : "text-u-text3",
      )}
    >
      {children}
    </li>
  );
}
