import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { WidgetLayout } from "../../dashboards/api/types";
import { DashboardGrid } from "../../dashboards/components/DashboardGrid";
import { tileLimits, type HomeTile } from "../lib/homeLayout";
import { tileKey } from "../lib/useHomeEditor";

/**
 * Home's tiles on the dashboard's twelve-column grid. In edit mode each tile is covered: the whole tile is the handle
 * (its links and buttons stay asleep underneath so a press anywhere drags), with the pencil for a chart and × for
 * anything but Needs you.
 */
export function HomeGrid({
  tiles,
  editing,
  labelOf,
  renderTile,
  onRelayout,
  onEditChart,
  onRemove,
  onAddChart,
}: {
  tiles: HomeTile[];
  editing: boolean;
  labelOf: (tile: HomeTile) => string;
  renderTile: (tile: HomeTile) => ReactNode;
  onRelayout: (layouts: WidgetLayout[]) => void;
  /** Null for a tile that cannot be edited: a section, or a chart whose query left the library. */
  onEditChart: (index: number) => (() => void) | null;
  onRemove: (index: number) => void;
  onAddChart: () => void;
}) {
  return (
    <div>
      <DashboardGrid
        editing={editing}
        items={tiles.map((tile, index) => ({
          key: tileKey(tile, index),
          layout: tile.layout,
          limits: tileLimits(tile),
          label: labelOf(tile),
        }))}
        onChange={onRelayout}
      >
        {(index) => {
          const tile = tiles[index];
          const label = labelOf(tile);
          const edit = onEditChart(index);
          return (
            <div className="relative h-full min-w-0">
              {renderTile(tile)}
              {editing && (
                <div className="absolute inset-0 z-10 rounded-u-card border-[1.5px] border-dashed border-u-accent-ring bg-u-accent-tint/40 group-hover/chart:border-u-accent">
                  <div className="absolute right-2 top-2 flex gap-1">
                    {edit && <TileButton label={`Edit ${label}`} icon={ICONS.pencil} onClick={edit} />}
                    {tile.kind !== "NEEDS" && (
                      <TileButton label={`Remove ${label}`} icon={ICONS.close} tone="danger" onClick={() => onRemove(index)} />
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        }}
      </DashboardGrid>
      {editing && (
        <button
          type="button"
          onClick={onAddChart}
          className="mt-3.5 flex w-full items-center justify-center gap-2 rounded-u-card border-[1.5px] border-dashed border-u-border-strong p-5 text-body font-medium text-u-text2 hover:border-u-accent-ring hover:text-u-accent"
        >
          <Icon d={ICONS.plus} size={15} />
          Add chart
        </button>
      )}
    </div>
  );
}

function TileButton({ label, icon, tone, onClick }: { label: string; icon: string; tone?: "danger"; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        "grid size-[26px] place-items-center rounded-u-control border border-u-border bg-u-surface text-u-text2 transition hover:text-u-text",
        tone === "danger" && "hover:border-u-offlimits hover:text-u-offlimits",
      )}
    >
      <Icon d={icon} size={13} />
    </button>
  );
}
