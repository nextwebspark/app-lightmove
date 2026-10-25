import { TabList } from "../../../components/ui/TabList";
import { DRAWER_TAB_LABELS, type DrawerTab } from "../lib/drawerTabs";

/**
 * Both person drawers' tab bar: one set of names, and a count only where it helps scanning. Text alone, as the
 * mockup draws it — seven tabs with glyphs no longer fit the drawer's width. A live copilot's tab is the exception:
 * its pulsing dot and the rule after it say it lasts only the call.
 */
export function DrawerTabBar({
  idPrefix,
  tabs,
  value,
  onChange,
  counts = {},
  copilotNudges = 0,
}: {
  idPrefix: string;
  tabs: readonly DrawerTab[];
  value: DrawerTab;
  onChange: (tab: DrawerTab) => void;
  counts?: Partial<Record<"notes" | "documents" | "positions", number>>;
  /** Suggestions the copilot holds while another tab is open. */
  copilotNudges?: number;
}) {
  return (
    <TabList
      label="Executive sections"
      idPrefix={idPrefix}
      className="-mb-px mt-3.5 gap-3.5"
      value={value}
      onChange={onChange}
      tabs={tabs.map((tab) =>
        tab === "copilot"
          ? {
              value: tab,
              label: DRAWER_TAB_LABELS[tab],
              icon: <span aria-hidden="true" className="me-0.5 size-1.5 animate-pulse rounded-full bg-u-offlimits" />,
              count: value === "copilot" ? undefined : copilotNudges,
              countTone: "accent" as const,
              dividerAfter: true,
            }
          : {
              value: tab,
              label: DRAWER_TAB_LABELS[tab],
              count: tab === "notes" || tab === "documents" || tab === "positions" ? counts[tab] : undefined,
            },
      )}
    />
  );
}
