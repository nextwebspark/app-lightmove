import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Toggle } from "../../../components/ui";
import { Drawer, DrawerFooter } from "../../../components/ui/Drawer";
import { cn } from "../../../lib/cn";
import type { ChartQueryDef } from "../../dashboards/api/types";
import {
  DEFAULT_HOME_LAYOUT,
  isDefaultLayout,
  MAX_PINNED_CHARTS,
  moveSection,
  type HomeLayout,
  type HomeSection,
  type Landing,
  type NeedsKind,
} from "../lib/homeLayout";

const SECTION_TEXT: Record<HomeSection, [string, string]> = {
  today: ["Today", "Your calls and meetings for today"],
  positions: ["Your positions", "Health and stage of the positions you are on"],
  charts: ["Pinned charts", "Headline figures from the dashboard library"],
};

const KIND_TEXT: [NeedsKind, string, string][] = [
  ["NEEDS_REPLY", "Replies", "Executives who wrote back"],
  ["FOLLOW_UP", "Follow-ups", "A call or an email you set for them"],
  ["PREPARATION", "Preparation", "Work to do before you approach them"],
  ["ATTENTION", "Needs attention", "A bounced email or a stopped sequence"],
];

const LANDINGS: [Landing, string, string][] = [
  ["home", "Home", "What needs you today"],
  ["positions", "Positions", "The list of positions"],
];

/** Customize Home: only the person's own layout changes, and Reset brings back the default everyone starts from. */
export function CustomizeHome({
  open,
  onClose,
  layout,
  onChange,
  library,
}: {
  open: boolean;
  onClose: () => void;
  layout: HomeLayout;
  onChange: (next: HomeLayout) => void;
  library: ChartQueryDef[];
}) {
  const headline = library.filter((query) => query.shape === "KPI");
  const pinnedFull = layout.charts.length >= MAX_PINNED_CHARTS;

  return (
    <Drawer open={open} onClose={onClose} label="Customize Home">
      <div className="flex items-start gap-3 border-b border-u-border px-5 py-4">
        <div>
          <h2 className="text-lead font-semibold">Customize Home</h2>
          <p className="mt-0.5 text-note text-u-text3">Only you see these changes.</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close" className="ml-auto grid size-8 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text">
          <Icon d={ICONS.close} size={15} />
        </button>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-5 py-4">
        <Group title="Sections">
          <Row label="Needs you" sub="Replies, follow-ups and your tasks" trailing={<span className="text-meta text-u-text3">Always shown</span>} />
          {layout.sections.map((section, index) => {
            const [label, sub] = SECTION_TEXT[section.key];
            return (
              <Row
                key={section.key}
                label={label}
                sub={sub}
                leading={
                  <span className="flex gap-0.5">
                    <MoveButton label={`Move ${label} up`} icon={ICONS.arrowUp} disabled={index === 0} onClick={() => onChange(moveSection(layout, section.key, -1))} />
                    <MoveButton
                      label={`Move ${label} down`}
                      icon={ICONS.arrowDown}
                      disabled={index === layout.sections.length - 1}
                      onClick={() => onChange(moveSection(layout, section.key, 1))}
                    />
                  </span>
                }
                trailing={
                  <Toggle
                    checked={section.on}
                    label={`Show ${label}`}
                    onChange={(on) =>
                      onChange({ ...layout, sections: layout.sections.map((own) => (own.key === section.key ? { ...own, on } : own)) })
                    }
                  />
                }
              />
            );
          })}
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

        {headline.length > 0 && (
          <Group title="Pinned charts" note={`Up to ${MAX_PINNED_CHARTS}.`}>
            {headline.map((query) => {
              const pinned = layout.charts.includes(query.id);
              return (
                <Row
                  key={query.id}
                  label={query.label}
                  sub={query.description}
                  trailing={
                    <Toggle
                      checked={pinned}
                      disabled={!pinned && pinnedFull}
                      label={`Pin ${query.label}`}
                      onChange={(on) =>
                        onChange({
                          ...layout,
                          charts: on ? [...layout.charts, query.id] : layout.charts.filter((id) => id !== query.id),
                        })
                      }
                    />
                  }
                />
              );
            })}
          </Group>
        )}

        <Group title="Open Uncava on">
          <div role="radiogroup" aria-label="Open Uncava on" className="flex flex-col gap-1.5 p-1.5">
            {LANDINGS.map(([key, label, sub]) => {
              const on = layout.landing === key;
              return (
                <button
                  key={key}
                  type="button"
                  role="radio"
                  aria-checked={on}
                  onClick={() => onChange({ ...layout, landing: key })}
                  className={cn(
                    "flex items-center gap-2.5 rounded-u-chip border px-3 py-2 text-left",
                    on ? "border-u-accent bg-u-accent-tint" : "border-u-border hover:border-u-text3",
                  )}
                >
                  <span className={cn("grid size-3.5 place-items-center rounded-full border-[1.5px]", on ? "border-u-accent" : "border-u-border-strong")}>
                    {on && <span className="size-1.5 rounded-full bg-u-accent" />}
                  </span>
                  <span className="text-body font-medium">{label}</span>
                  <span className="ml-auto text-meta text-u-text3">{sub}</span>
                </button>
              );
            })}
          </div>
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
  leading,
  trailing,
}: {
  label: string;
  sub: string;
  leading?: React.ReactNode;
  trailing: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-2.5 border-t border-u-border px-3 py-2.5 first:border-t-0">
      {leading}
      <div className="min-w-0 flex-1">
        <div className="text-body font-medium">{label}</div>
        <div className="text-meta text-u-text3">{sub}</div>
      </div>
      {trailing}
    </div>
  );
}

function MoveButton({ label, icon, disabled, onClick }: { label: string; icon: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-6 place-items-center rounded-u-control text-u-text3 hover:bg-u-raised hover:text-u-text disabled:opacity-35 disabled:hover:bg-transparent"
    >
      <Icon d={icon} size={13} />
    </button>
  );
}
