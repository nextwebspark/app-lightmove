import { useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { NavLink } from "react-router-dom";
import { useAuth } from "../../../features/auth/AuthProvider";
import { useHelp } from "../../../features/help/HelpProvider";
import { useTheme } from "../../../features/theme/useTheme";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import { useFocusTrap } from "../../../lib/useFocusTrap";
import { APP_VERSION } from "../../../lib/version";
import { Icon, ICONS } from "../Icon";
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
  const { user, signOut } = useAuth();
  const { openHelp } = useHelp();
  const { theme, toggle: toggleTheme } = useTheme();
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
                <AccountRow>
                  <NavLink to="/settings/profile" onClick={onClose} className={rowClass(false, "rounded-none")}>
                    <Icon d={ICONS.profile} size={18} className="flex-none text-u-text3" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{user.fullName}</span>
                      <span className="block truncate text-meta text-u-text3">{user.email}</span>
                    </span>
                    <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
                  </NavLink>
                </AccountRow>
                <AccountRow>
                  <button
                    type="button"
                    role="switch"
                    aria-checked={theme === "dark"}
                    onClick={toggleTheme}
                    className={rowClass(false, "rounded-none")}
                  >
                    <Icon d={ICONS.moon} size={18} className="flex-none text-u-text3" />
                    <span className="flex-1 text-start">Dark mode</span>
                    <span
                      aria-hidden="true"
                      className={cn(
                        "relative h-6 w-10 flex-none rounded-full transition",
                        theme === "dark" ? "bg-u-accent" : "bg-u-border-strong",
                      )}
                    >
                      <span
                        className={cn(
                          "absolute top-0.5 size-5 rounded-full bg-white shadow transition-[inset-inline-start]",
                          theme === "dark" ? "start-[18px]" : "start-0.5",
                        )}
                      />
                    </span>
                  </button>
                </AccountRow>
                <AccountRow>
                  <button
                    type="button"
                    onClick={() => {
                      onClose();
                      openHelp();
                    }}
                    className={rowClass(false, "rounded-none")}
                  >
                    <Icon d={ICONS.help} size={18} className="flex-none text-u-text3" />
                    <span className="flex-1 text-start">Help</span>
                    <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
                  </button>
                </AccountRow>
                <AccountRow>
                  <button
                    type="button"
                    onClick={() => void signOut()}
                    className={rowClass(false, "rounded-none text-u-offlimits")}
                  >
                    <Icon d={ICONS.signOut} size={18} className="flex-none" />
                    <span className="flex-1 text-start">Sign out</span>
                  </button>
                </AccountRow>
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

function AccountRow({ children }: { children: ReactNode }) {
  return <li className="border-b border-u-border last:border-b-0">{children}</li>;
}

function rowClass(isActive: boolean, extra?: string) {
  return cn(
    "flex min-h-[52px] w-full items-center gap-3 rounded-u-chip px-3 py-2 text-body transition",
    "focus-visible:bg-u-raised focus-visible:outline-none active:bg-u-raised",
    isActive ? "bg-u-raised font-medium text-u-text [&_svg:first-child]:text-u-accent" : "text-u-text2 hover:bg-u-raised",
    extra,
  );
}
