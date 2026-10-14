import { TabList } from "../../../components/ui/TabList";
import { DRAWER_TAB_LABELS, type DrawerTab } from "../lib/drawerTabs";

/**
 * Both person drawers' tab bar: one set of names, and a count only where it helps scanning. Text alone, as the
 * mockup draws it — seven tabs with glyphs no longer fit the drawer's width.
 */
export function DrawerTabBar({
  idPrefix,
  tabs,
  value,
  onChange,
  counts = {},
}: {
  idPrefix: string;
  tabs: readonly DrawerTab[];
  value: DrawerTab;
  onChange: (tab: DrawerTab) => void;
  counts?: Partial<Record<"notes" | "documents" | "positions", number>>;
}) {
  return (
    <TabList
      label="Executive sections"
      idPrefix={idPrefix}
      className="-mb-px mt-3.5 gap-3.5"
      value={value}
      onChange={onChange}
      tabs={tabs.map((tab) => ({
        value: tab,
        label: DRAWER_TAB_LABELS[tab],
        count: tab === "notes" || tab === "documents" || tab === "positions" ? counts[tab] : undefined,
      }))}
    />
  );
}
