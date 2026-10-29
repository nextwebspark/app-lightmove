import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";

export interface AdvancedMenuItem {
  key: string;
  label: string;
  icon: string;
  /** A second line under the label: what it acts on, or why it is unavailable. */
  hint?: string;
  /** A small count beside the label, such as "3 hidden". */
  badge?: string;
  disabled?: boolean;
  /** A setting switched in place; the menu stays open so the change can be seen. */
  checked?: boolean;
  onSelect?: () => void;
  /** A panel the menu swaps to in place of acting, with a Back row above it. */
  panel?: () => ReactNode;
}

export interface AdvancedMenuSection {
  label: string;
  items: AdvancedMenuItem[];
}

const TRIGGER =
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border border-u-border-strong bg-u-surface " +
  "py-2 pl-3 pr-2.5 font-sans text-body font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text " +
  "aria-expanded:border-u-text3 aria-expanded:text-u-text";

/**
 * The toolbar's Advanced ▾: whatever In universe offers beyond its three primary actions, grouped, and
 * different per view (`Position.dc.html`'s `advancedItems`). `dot` names a setting inside that is on.
 */
export function AdvancedMenu({ sections, dot }: { sections: AdvancedMenuSection[]; dot?: string }) {
  return (
    <Popover
      align="right"
      width={288}
      hasPopup="menu"
      title={dot}
      triggerClassName={TRIGGER}
      trigger={(open) => (
        <>
          <Icon d={ICONS.sliders} size={14} className="flex-none" />
          Advanced
          {dot && <span aria-hidden="true" className="size-1.5 flex-none rounded-full bg-u-accent" />}
          <Icon
            d={ICONS.chevronDown}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
          />
        </>
      )}
    >
      {(close) => <AdvancedMenuPanel sections={sections} close={close} />}
    </Popover>
  );
}

function AdvancedMenuPanel({ sections, close }: { sections: AdvancedMenuSection[]; close: () => void }) {
  const [openPanel, setOpenPanel] = useState<string | null>(null);
  const panelItem = sections.flatMap((section) => section.items).find((item) => item.key === openPanel);

  if (panelItem?.panel) {
    return (
      <div>
        <button
          type="button"
          onClick={() => setOpenPanel(null)}
          className="flex w-full items-center gap-2 rounded-md px-2 py-[7px] text-start font-sans text-note font-semibold text-u-text hover:bg-u-raised"
        >
          <Icon d={ICONS.back} size={14} className="flex-none text-u-text3" />
          {panelItem.label}
        </button>
        <div className="mx-1 mb-1.5 mt-1 h-px bg-u-border" />
        {panelItem.panel()}
      </div>
    );
  }

  return (
    <div role="menu" aria-label="Advanced">
      {sections.map((section, index) => (
        <div key={section.label} role="group" aria-label={section.label}>
          {index > 0 && <div className="mx-1 my-1.5 h-px bg-u-border" />}
          <div className="px-2 pb-1 pt-2 type-micro-label text-u-text3">{section.label}</div>
          {section.items.map((item) => (
            <MenuRow
              key={item.key}
              item={item}
              onActivate={() => {
                if (item.disabled) return;
                if (item.panel) {
                  setOpenPanel(item.key);
                  return;
                }
                if (item.checked === undefined) close();
                item.onSelect?.();
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

function MenuRow({ item, onActivate }: { item: AdvancedMenuItem; onActivate: () => void }) {
  const isToggle = item.checked !== undefined;
  return (
    <button
      type="button"
      role={isToggle ? "menuitemcheckbox" : "menuitem"}
      aria-checked={isToggle ? item.checked : undefined}
      aria-disabled={item.disabled || undefined}
      onClick={onActivate}
      className={cn(
        "flex w-full items-start gap-2.5 rounded-md px-2 py-[7px] text-start font-sans text-body font-medium",
        item.disabled ? "cursor-default text-u-text3 opacity-60" : "text-u-text2 hover:bg-u-raised hover:text-u-text",
      )}
    >
      <Icon d={item.icon} size={14} className="mt-px flex-none text-u-text3" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span>{item.label}</span>
        {item.hint && <span className="text-meta font-normal text-u-text3">{item.hint}</span>}
      </span>
      {item.badge && (
        <span className="flex-none rounded bg-u-accent-tint px-[5px] py-[2px] text-eyebrow font-bold text-u-accent">
          {item.badge}
        </span>
      )}
      {isToggle && (
        <span
          aria-hidden="true"
          className={cn(
            "relative mt-px h-4 w-7 flex-none rounded-full transition-colors",
            item.checked ? "bg-u-accent-solid" : "bg-u-border-strong",
          )}
        >
          <span
            className={cn(
              "absolute top-0.5 size-3 rounded-full bg-white transition-[left]",
              item.checked ? "left-3.5" : "left-0.5",
            )}
          />
        </span>
      )}
      {item.panel && <Icon d={ICONS.chevronRight} size={13} className="mt-px flex-none text-u-text3" />}
    </button>
  );
}
