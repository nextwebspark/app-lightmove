import { useRef } from "react";
import { createPortal } from "react-dom";
import { NavLink } from "react-router-dom";
import { useAuth } from "../../../features/auth/AuthProvider";
import { useHelp } from "../../../features/help/HelpProvider";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import { useFocusTrap } from "../../../lib/useFocusTrap";
import { APP_VERSION } from "../../../lib/version";
import { Toggle } from "../../ui";
import { Icon, ICONS } from "../Icon";
import { useAccountActions, type AccountAction } from "../useAccountActions";
import type { SidebarGroup, SidebarItem } from "../Sidebar";

export const MOBILE_MORE_ID = "mobile-more";

/**
 * The phone's More: a panel from the right edge with the rail's every screen as a full-width row, and the account
 * underneath — the pattern a phone's own apps teach, where the desktop rail squeezed into a card was not.
 */
export function MobileMoreSheet({
  open,
  onClose,
  title,
  groups,
  backLink,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  groups: readonly SidebarGroup[];
  backLink?: SidebarItem;
}) {
  const { user } = useAuth();
  const { openHelp } = useHelp();
  const accountActions = useAccountActions();
  const panelRef = useRef<HTMLDivElement>(null);
  useEscapeKey(open, onClose);
  useFocusTrap(panelRef, open, { startAt: "panel" });

  if (!open) return null;

  return createPortal(
    <div className="lg:hidden">
      <div className="fixed inset-0 z-[96] bg-u-scrim" onClick={onClose} />
      <div
        ref={panelRef}
        id={MOBILE_MORE_ID}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="More"
        className={cn(
          "fixed inset-y-0 end-0 z-[97] flex w-[min(360px,88vw)] animate-slide-in-end flex-col outline-none",
          "border-s border-u-border-strong bg-u-surface shadow-u-e3",
          "pb-[env(safe-area-inset-bottom)] pt-[env(safe-area-inset-top)]",
        )}
      >
        <div className="flex flex-none items-center gap-3 border-b border-u-border py-3 pe-2 ps-5">
          <h2 className="min-w-0 flex-1 truncate text-lead font-semibold text-u-text">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="grid size-11 flex-none place-items-center rounded-full text-u-text2 transition hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={20} />
          </button>
        </div>

        <nav aria-label="All screens" className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
          {backLink && (
            <NavLink to={backLink.to} onClick={onClose} className={rowClass(false, "mt-3")}>
              <Icon d={backLink.icon} size={18} className="flex-none text-u-text3" />
              <span className="min-w-0 flex-1 truncate">{backLink.label}</span>
            </NavLink>
          )}

          {groups.map((group) => (
            <section key={group.label} aria-label={group.label}>
              <h3 className="px-3 pb-1.5 pt-5 type-summary-label text-u-text3">{group.label}</h3>
              <ul className="overflow-hidden rounded-u-card border border-u-border">
                {group.items.map((item) => (
                  <li key={item.to} className="border-b border-u-border last:border-b-0">
                    <NavLink
                      to={item.to}
                      end={item.end}
                      onClick={onClose}
                      className={({ isActive }) => rowClass(isActive, "rounded-none")}
                    >
                      <Icon d={item.icon} size={18} className="flex-none text-u-text3" />
                      <span className="min-w-0 flex-1 truncate">{item.label}</span>
                      {item.count !== undefined && (
                        <span className="flex-none font-mono text-meta text-u-text3">{item.count}</span>
                      )}
                      <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
                    </NavLink>
                  </li>
                ))}
              </ul>
            </section>
          ))}

          {user && (
            <section aria-label="Account">
              <h3 className="px-3 pb-1.5 pt-5 type-summary-label text-u-text3">Account</h3>
              <ul className="overflow-hidden rounded-u-card border border-u-border">
                <li className="border-b border-u-border px-3 py-2.5">
                  <span className="block truncate text-body font-medium text-u-text">{user.fullName}</span>
                  <span className="block truncate text-meta text-u-text3">{user.email}</span>
                </li>
                {accountActions.map((action) => (
                  <li key={action.key} className="border-b border-u-border last:border-b-0">
                    <AccountRow action={action} onClose={onClose} />
                  </li>
                ))}
              </ul>
            </section>
          )}

          <button
            type="button"
            onClick={() => {
              onClose();
              openHelp("whatsNew");
            }}
            className="mt-5 px-3 font-mono text-meta text-u-text3 underline decoration-dotted underline-offset-2"
          >
            {APP_VERSION} · What's new
          </button>
        </nav>
      </div>
    </div>,
    document.body,
  );
}

function AccountRow({ action, onClose }: { action: AccountAction; onClose: () => void }) {
  const icon = (
    <Icon d={action.icon} size={18} className={cn("flex-none", action.key !== "signOut" && "text-u-text3")} />
  );
  const tone = action.key === "signOut" ? "rounded-none text-u-offlimits" : "rounded-none";
  if (action.to) {
    return (
      <NavLink to={action.to} onClick={onClose} className={rowClass(false, tone)}>
        {icon}
        <span className="min-w-0 flex-1 truncate">{action.label}</span>
        <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
      </NavLink>
    );
  }
  if (action.checked !== undefined) {
    // A label round the switch, so the whole row flips it rather than the 32px pill alone.
    return (
      <label className={rowClass(false, cn(tone, "cursor-pointer"))}>
        {icon}
        <span className="flex-1">{action.label}</span>
        <Toggle checked={action.checked} onChange={() => action.onSelect?.()} label={action.label} />
      </label>
    );
  }
  return (
    <button
      type="button"
      onClick={() => {
        if (action.key === "help") onClose();
        action.onSelect?.();
      }}
      className={rowClass(false, tone)}
    >
      {icon}
      <span className="flex-1 text-start">{action.label}</span>
    </button>
  );
}

function rowClass(isActive: boolean, extra?: string) {
  return cn(
    "flex min-h-[52px] w-full items-center gap-3 rounded-u-chip px-3 py-2 text-body transition",
    "focus-visible:bg-u-raised focus-visible:outline-none active:bg-u-raised",
    isActive ? "bg-u-raised font-medium text-u-text [&_svg:first-child]:text-u-accent" : "text-u-text2 hover:bg-u-raised",
    extra,
  );
}
