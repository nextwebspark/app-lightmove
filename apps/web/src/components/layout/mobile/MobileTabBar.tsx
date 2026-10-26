import { Link, matchPath } from "react-router-dom";
import { cn } from "../../../lib/cn";
import { Icon, ICONS } from "../Icon";
import { MOBILE_MORE_ID } from "./MobileMoreSheet";

export interface MobileTab {
  to: string;
  label: string;
  icon: string;
  /** Ends the match at the exact path — "/" would otherwise match everything. */
  end?: boolean;
  /** Another path that also lights this tab, as every Companies stage lights Companies. */
  activeWhen?: (pathname: string) => boolean;
}

/**
 * The phone's bottom bar below `lg`: the screens a researcher works in, a raised + for the actions they take
 * most, and More, which opens the full rail — so nothing the rail offers is out of reach.
 */
export function MobileTabBar({
  tabs,
  pathname,
  onMore,
  moreOpen,
  onQuickAction,
  quickActionLabel = "Quick actions",
}: {
  tabs: readonly MobileTab[];
  pathname: string;
  onMore: () => void;
  moreOpen: boolean;
  /** The centre +; absent where the viewer has nothing to add. */
  onQuickAction?: () => void;
  quickActionLabel?: string;
}) {
  const half = Math.ceil(tabs.length / 2);
  const leading = onQuickAction ? tabs.slice(0, half) : tabs;
  const trailing = onQuickAction ? tabs.slice(half) : [];

  return (
    <nav
      aria-label="Quick navigation"
      data-mobile-nav=""
      className="fixed inset-x-0 bottom-0 z-[85] border-t border-u-border-strong bg-u-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden"
    >
      <ul className="mx-auto flex h-14 max-w-xl items-stretch">
        {leading.map((tab) => (
          <TabItem key={tab.to} tab={tab} pathname={pathname} />
        ))}
        {onQuickAction && (
          <li className="flex flex-1 items-center justify-center">
            <button
              type="button"
              onClick={onQuickAction}
              aria-label={quickActionLabel}
              className="grid size-12 -translate-y-2 place-items-center rounded-full bg-u-accent text-u-bg shadow-u-e3 transition active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent"
            >
              <Icon d={ICONS.plus} size={22} />
            </button>
          </li>
        )}
        {trailing.map((tab) => (
          <TabItem key={tab.to} tab={tab} pathname={pathname} />
        ))}
        <li className="flex flex-1">
          <button
            type="button"
            onClick={onMore}
            aria-expanded={moreOpen}
            aria-controls={MOBILE_MORE_ID}
            className={cn(tabClass(false), moreOpen && "text-u-text")}
          >
            <Icon d={ICONS.menu} size={20} />
            <span>More</span>
          </button>
        </li>
      </ul>
    </nav>
  );
}

function TabItem({ tab, pathname }: { tab: MobileTab; pathname: string }) {
  const target = tab.to.split("?")[0];
  const isActive =
    !!matchPath({ path: target, end: tab.end ?? false }, pathname) || (tab.activeWhen?.(pathname) ?? false);
  return (
    <li className="flex flex-1">
      <Link to={tab.to} className={tabClass(isActive)} aria-current={isActive ? "page" : undefined}>
        <Icon d={tab.icon} size={20} />
        <span className="max-w-full truncate">{tab.label}</span>
      </Link>
    </li>
  );
}

function tabClass(isActive: boolean) {
  return cn(
    "flex w-full min-w-0 flex-col items-center justify-center gap-0.5 px-1 text-meta font-medium transition",
    "focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-u-accent",
    isActive ? "text-u-text [&_svg]:text-u-accent" : "text-u-text3 hover:text-u-text2",
  );
}
