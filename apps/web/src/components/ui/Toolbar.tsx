import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * The toolbar row Strategy, In universe and Outreach share (`Strategy.dc.html`, `Position.dc.html`,
 * `Outreach.dc.html`): search first, the page's own controls beside it, its actions pushed right.
 */
export const TOOLBAR_ROW =
  "flex flex-none flex-wrap items-center gap-x-3 gap-y-2 border-b border-u-border bg-u-surface px-4 py-2.5";

const TOOLBAR_CONTROL =
  "inline-flex h-[34px] flex-none items-center gap-[7px] whitespace-nowrap rounded-u-control font-sans text-body transition";

/** A secondary action, bordered. Greyed through `aria-disabled`, so it still takes focus and reads its title. */
export const TOOLBAR_OUTLINE = cn(
  TOOLBAR_CONTROL,
  "border border-u-border-strong bg-u-surface px-3 font-medium text-u-text hover:bg-u-raised",
  "aria-disabled:cursor-not-allowed aria-disabled:opacity-50 aria-disabled:hover:bg-u-surface",
);

/** The row's one filled action. */
export const TOOLBAR_PRIMARY = cn(
  TOOLBAR_CONTROL,
  "bg-u-accent-solid px-3.5 font-semibold text-white hover:bg-u-accent-solid-hover",
  "aria-disabled:cursor-not-allowed aria-disabled:opacity-50 aria-disabled:hover:bg-u-accent-solid",
  "disabled:cursor-not-allowed disabled:opacity-50",
);

/** A quiet control: no border, a fill only while hovered. */
export const TOOLBAR_GHOST = cn(
  TOOLBAR_CONTROL,
  "px-2.5 text-u-text2 hover:bg-u-raised hover:text-u-text disabled:cursor-not-allowed disabled:opacity-40",
);

/** A quiet control that opens a popover, filled while it is open. */
export const TOOLBAR_MENU = cn(TOOLBAR_GHOST, "aria-expanded:bg-u-raised aria-expanded:text-u-text");

/** A count beside a ghost control's label, such as the filters in force or the columns hidden. */
export const TOOLBAR_COUNT =
  "rounded-[4px] border border-u-border bg-u-raised px-1.5 py-px font-sans text-[10.5px] font-medium text-u-text2";

/** The count that is the control's point, such as the saved searches. */
export const TOOLBAR_COUNT_ACCENT =
  "rounded-[4px] bg-u-accent-tint px-[5px] py-[2px] font-sans text-[10px] font-bold text-u-accent";

export function ToolbarDivider() {
  return <span aria-hidden="true" className="h-5 w-px flex-none bg-u-border" />;
}

export function ToolbarSearch({
  value,
  onChange,
  label,
  placeholder,
  maxLength,
  onEnter,
  className,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder: string;
  maxLength?: number;
  /** Enter as a press, for a page whose results wait for one. */
  onEnter?: () => void;
  className?: string;
}) {
  return (
    <label
      className={cn(
        "flex min-w-[200px] flex-[1_1_280px] items-center gap-2 rounded-u-control border border-u-border-strong bg-u-surface py-2 pl-3 pr-2.5 sm:max-w-[360px]",
        className,
      )}
    >
      <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={label}
        maxLength={maxLength}
        onKeyDown={(event) => {
          if (!onEnter || event.key !== "Enter" || event.nativeEvent.isComposing) return;
          event.preventDefault();
          onEnter();
        }}
        className="w-full min-w-0 bg-transparent font-sans text-body text-u-text outline-none placeholder:text-u-text3 [&::-webkit-search-cancel-button]:hidden"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange("")}
          aria-label="Clear search"
          className="grid size-5 flex-none place-items-center rounded text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.close} size={12} />
        </button>
      )}
    </label>
  );
}
