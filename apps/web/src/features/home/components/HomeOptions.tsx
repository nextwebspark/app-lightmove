import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Toggle } from "../../../components/ui";
import { Drawer, DrawerFooter } from "../../../components/ui/Drawer";
import { useAuth } from "../../auth/AuthProvider";
import { startPageLabel } from "../../startpage/lib/startPage";
import {
  DEFAULT_HOME_LAYOUT,
  hasSection,
  isDefaultLayout,
  withSection,
  type HomeLayout,
  type NeedsKind,
} from "../lib/homeLayout";

const SECTION_TEXT: ["TODAY" | "POSITIONS", string, string][] = [
  ["TODAY", "Today", "Your calls and meetings for today"],
  ["POSITIONS", "Your positions", "Health and stage of the positions you are on"],
];

const KIND_TEXT: [NeedsKind, string, string][] = [
  ["NEEDS_REPLY", "Needs reply", "Executives who wrote back"],
  ["FOLLOW_UP", "Follow-ups", "A call or an email you set for them"],
  ["PREPARATION", "Before first email", "Work to do before you approach them"],
  ["ATTENTION", "Delivery problems", "A bounced email or a stopped sequence"],
];

/**
 * Home options, beside the grid while Home is being edited: which sections are on it and what Needs you shows. Like
 * every change in edit mode it waits for Save layout; Reset brings back the default everyone starts from. Where Uncava
 * opens is Settings → Profile's, which this only names.
 */
export function HomeOptions({
  open,
  onClose,
  layout,
  onChange,
}: {
  open: boolean;
  onClose: () => void;
  layout: HomeLayout;
  onChange: (next: HomeLayout) => void;
}) {
  const { user } = useAuth();
  return (
    <Drawer open={open} onClose={onClose} label="Home options">
      <div className="flex items-start gap-3 border-b border-u-border px-5 py-4">
        <div>
          <h2 className="text-lead font-semibold">Home options</h2>
          <p className="mt-0.5 text-note text-u-text3">Only you see these. Nothing changes until you save the layout.</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close" className="ml-auto grid size-8 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text">
          <Icon d={ICONS.close} size={15} />
        </button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-5 py-4">
        <Group title="Sections">
          <Row label="Needs you" sub="Replies, follow-ups and your tasks" trailing={<span className="text-meta text-u-text3">Always shown</span>} />
          {SECTION_TEXT.map(([section, label, sub]) => (
            <Row
              key={section}
              label={label}
              sub={sub}
              trailing={
                <Toggle checked={hasSection(layout, section)} label={`Show ${label}`} onChange={(on) => onChange(withSection(layout, section, on))} />
              }
            />
          ))}
        </Group>

        <Group title="What Needs you shows" note="Your own tasks always show.">
          {KIND_TEXT.map(([kind, label, sub]) => (
            <Row
              key={kind}
              label={label}
              sub={sub}
              trailing={
                <Toggle checked={layout.kinds[kind]} label={`Show ${label}`} onChange={(on) => onChange({ ...layout, kinds: { ...layout.kinds, [kind]: on } })} />
              }
            />
          ))}
        </Group>

        <Group title="Open Uncava on">
          <Row
            label={startPageLabel(user?.startPage)}
            sub="Your start page in this workspace"
            trailing={
              <Link to="/settings/profile#start-page" className="shrink-0 text-note font-medium text-u-accent hover:underline">
                Change in Settings
              </Link>
            }
          />
        </Group>
      </div>

      <DrawerFooter>
        <Button variant="ghost" size="sm" disabled={isDefaultLayout(layout)} onClick={() => onChange(DEFAULT_HOME_LAYOUT)}>
          Reset to the default
        </Button>
        <Button size="sm" className="ml-auto" onClick={onClose}>
          Done
        </Button>
      </DrawerFooter>
    </Drawer>
  );
}

function Group({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="type-micro-label pb-1.5 text-u-text3">{title}</h3>
      {note && <p className="pb-2 text-meta text-u-text3">{note}</p>}
      <div className="overflow-hidden rounded-u-chip border border-u-border">{children}</div>
    </div>
  );
}

function Row({
  label,
  sub,
  trailing,
}: {
  label: string;
  sub: string;
  trailing: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-2.5 border-t border-u-border px-3 py-2.5 first:border-t-0">
      <div className="min-w-0 flex-1">
        <div className="text-body font-medium">{label}</div>
        <div className="text-meta text-u-text3">{sub}</div>
      </div>
      {trailing}
    </div>
  );
}
