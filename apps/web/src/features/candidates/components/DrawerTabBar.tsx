import { Icon, ICONS } from "../../../components/layout/Icon";
import { TabList } from "../../../components/ui/TabList";
import { DRAWER_TAB_LABELS, type DrawerTab } from "../lib/drawerTabs";

const TAB_ICONS: Readonly<Record<DrawerTab, string>> = {
  career: ICONS.briefcase,
  notes: ICONS.fileText,
  contact: ICONS.mail,
  documents: ICONS.file,
  activity: ICONS.activity,
  positions: ICONS.position,
};

/** Both person drawers' tab bar: one set of names and glyphs, and a count only where it helps scanning. */
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
        icon: <Icon d={TAB_ICONS[tab]} size={13} className="flex-none" />,
        count: tab === "notes" || tab === "documents" || tab === "positions" ? counts[tab] : undefined,
      }))}
    />
  );
}
