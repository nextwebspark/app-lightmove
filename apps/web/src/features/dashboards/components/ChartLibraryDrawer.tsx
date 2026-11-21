import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Drawer, Field, Input } from "../../../components/ui";
import { DrawerFooter } from "../../../components/ui/Drawer";
import { ListboxSelect } from "../../../components/ui/ListboxSelect";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import * as dashboardsApi from "../api/dashboardsApi";
import type { ChartQueryDef, ChartType, DashboardScope, DashboardWidget } from "../api/types";
import {
  allowedCharts,
  CHART_ICONS,
  CHART_LABELS,
  defaultOptions,
  defaultTitle,
  optionSummary,
  pickerCharts,
  SHAPE_LABELS,
} from "../lib/chartRules";
import { useRelabel } from "../lib/useRelabel";
import { DashboardChart } from "./DashboardChart";

export const CHART_TITLE_LIMIT = 80;

const ALL = "All";

/** A chart being configured: a new one from the library, or one already on the dashboard (`index`). */
export interface ChartDraft {
  query: ChartQueryDef;
  chart: ChartType;
  options: Record<string, string>;
  title: string;
  index: number | null;
}

export function draftFor(query: ChartQueryDef, widget?: DashboardWidget, index: number | null = null): ChartDraft {
  return {
    query,
    chart: widget?.chart ?? query.defaultChart,
    options: { ...defaultOptions(query), ...(widget?.options ?? {}) },
    title: widget?.title ?? "",
    index,
  };
}

/**
 * Add chart: the library of ready-made queries, by category with a search, then "Show as" — only the chart types
 * that fit the data can be picked — with the query's options, a title and a live preview.
 */
export function ChartLibraryDrawer({
  scope,
  library,
  onCountLabel,
  initialDraft,
  onCommit,
  onClose,
  addLabel = "Add to dashboard",
}: {
  scope: DashboardScope;
  library: ChartQueryDef[];
  onCountLabel: string;
  /** The button that adds a new chart: "Add to dashboard", or "Add to Home". */
  addLabel?: string;
  /** Opens straight on "Show as" for a chart already on the dashboard. */
  initialDraft: ChartDraft | null;
  onCommit: (draft: ChartDraft) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<ChartDraft | null>(initialDraft);
  const editingExisting = initialDraft !== null;
  return (
    <Drawer open onClose={onClose} label="Chart library" wide>
      {draft ? (
        <ShowAs
          scope={scope}
          draft={draft}
          addLabel={addLabel}
          onChange={setDraft}
          onBack={() => (editingExisting ? onClose() : setDraft(null))}
          onCommit={(final) => {
            onCommit(final);
            if (editingExisting) onClose();
            else setDraft(null);
          }}
        />
      ) : (
        <Library library={library} countLabel={onCountLabel} onPick={(query) => setDraft(draftFor(query))}
                 onClose={onClose} />
      )}
    </Drawer>
  );
}

function Library({
  library,
  countLabel,
  onPick,
  onClose,
}: {
  library: ChartQueryDef[];
  countLabel: string;
  onPick: (query: ChartQueryDef) => void;
  onClose: () => void;
}) {
  const relabel = useRelabel();
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState(ALL);
  const categories = useMemo(() => [ALL, ...new Set(library.map((query) => query.category))], [library]);
  const needle = search.trim().toLowerCase();
  const shown = library.filter(
    (query) =>
      (category === ALL || query.category === category) &&
      (!needle || relabel(`${query.label} ${query.description} ${query.category}`).toLowerCase().includes(needle)),
  );
  const groups = categories
    .filter((name) => name !== ALL)
    .map((name) => ({ name, queries: shown.filter((query) => query.category === name) }))
    .filter((group) => group.queries.length > 0);

  return (
    <>
      <div className="border-b border-u-border px-5 pb-3.5 pt-5">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-subhead font-semibold">Add chart</h2>
            <p className="mt-1 text-note text-u-text3">{countLabel}</p>
          </div>
          <button type="button" onClick={onClose} aria-label="Close"
                  className="grid size-[30px] place-items-center rounded-u-control text-u-text2 hover:bg-u-raised hover:text-u-text">
            <Icon d={ICONS.close} size={16} />
          </button>
        </div>
        <div className="relative mt-3.5">
          <Icon d={ICONS.search} size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-u-text3" />
          <Input value={search} onChange={(event) => setSearch(event.target.value)} aria-label="Search charts"
                 placeholder="Search charts — e.g. sector, pace, gender" className="pl-9" />
        </div>
        <div role="group" aria-label="Categories" className="mt-3 flex gap-1.5 overflow-x-auto">
          {categories.map((name) => (
            <button key={name} type="button" aria-pressed={category === name} onClick={() => setCategory(name)}
                    className={cn(
                      "flex-none whitespace-nowrap rounded-full border px-3 py-1 text-note font-medium",
                      category === name
                        ? "border-u-accent-solid bg-u-accent-solid text-white"
                        : "border-u-border-strong text-u-text2 hover:text-u-text",
                    )}>
              {name}
            </button>
          ))}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-5 pb-6">
        {groups.map((group) => (
          <div key={group.name}>
            <div className="type-micro-label mb-1 mt-4">{group.name}</div>
            {group.queries.map((query) => (
              <button key={query.id} type="button" onClick={() => onPick(query)}
                      className="flex w-full items-center gap-3 rounded-u-control border-t border-u-border px-1.5 py-3 text-left hover:bg-u-raised">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-body font-semibold">
                    {relabel(query.label)}
                  </div>
                  <div className="mt-0.5 text-note text-u-text2">{relabel(query.description)}</div>
                  <div className="mt-1.5 flex items-center gap-1.5 text-u-text3">
                    <span className="text-meta">{SHAPE_LABELS[query.shape]} ·</span>
                    {query.charts.map((chart) => (
                      <span key={chart} title={CHART_LABELS[chart]}>
                        <Icon d={CHART_ICONS[chart]} size={14} />
                      </span>
                    ))}
                  </div>
                </div>
                <Icon d={ICONS.chevronRight} size={16} className="flex-none text-u-text3" />
              </button>
            ))}
          </div>
        ))}
        {groups.length === 0 && <p className="py-10 text-center text-body text-u-text3">No chart matches that search.</p>}
      </div>
      <DrawerFooter>
        <Button variant="secondary" type="button" onClick={onClose}>
          Done
        </Button>
      </DrawerFooter>
    </>
  );
}

function ShowAs({
  scope,
  draft,
  onChange,
  addLabel,
  onBack,
  onCommit,
}: {
  scope: DashboardScope;
  draft: ChartDraft;
  addLabel: string;
  onChange: (draft: ChartDraft) => void;
  onBack: () => void;
  onCommit: (final: ChartDraft) => void;
}) {
  const relabel = useRelabel();
  const { query } = draft;
  const { data: result, isPending, isError } = useQuery({
    queryKey: dashboardsApi.CHART_RESULT_KEY(scope, query.id, draft.options),
    queryFn: ({ signal }) => dashboardsApi.runChartQuery(scope, query.id, draft.options, signal),
  });
  const allowed = result ? result.allowedCharts : allowedCharts(query.shape, query.notAdditive, 0);
  const chart = allowed.includes(draft.chart) ? draft.chart : allowed[0];
  const title = draft.title.trim() || relabel(defaultTitle(query, draft.options));

  return (
    <>
      <div className="flex items-start gap-2.5 border-b border-u-border px-5 pb-3.5 pt-4">
        <button type="button" onClick={onBack} aria-label="Back"
                className="grid size-[30px] flex-none place-items-center rounded-u-control text-u-text2 hover:bg-u-raised hover:text-u-text">
          <Icon d={ICONS.arrowLeft} size={16} />
        </button>
        <div className="min-w-0">
          <h2 className="text-subhead font-semibold">{relabel(defaultTitle(query, draft.options))}</h2>
          <p className="mt-0.5 text-note text-u-text3">{relabel(query.description)}</p>
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-5 py-4">
        <div className="type-micro-label mb-1.5" id="show-as">Show as</div>
        <div role="radiogroup" aria-labelledby="show-as" className="flex flex-wrap gap-1.5">
          {pickerCharts(query.shape).map((type) => {
            const fits = allowed.includes(type);
            const on = chart === type;
            return (
              <button key={type} type="button" role="radio" aria-checked={on} aria-disabled={!fits}
                      title={fits ? CHART_LABELS[type] : `${CHART_LABELS[type]} doesn't fit this data`}
                      onClick={() => fits && onChange({ ...draft, chart: type })}
                      className={cn(
                        "flex min-w-16 flex-col items-center gap-1 rounded-u-chip border px-2 py-2 text-meta font-medium",
                        on ? "border-u-accent bg-u-accent-tint text-u-accent" : "border-u-border-strong text-u-text2",
                        fits ? "cursor-pointer hover:text-u-text" : "cursor-not-allowed opacity-40",
                      )}>
                <Icon d={CHART_ICONS[type]} size={18} />
                {CHART_LABELS[type]}
              </button>
            );
          })}
        </div>
        <p className="mt-1.5 text-meta text-u-text3">Only the chart types that fit this data can be picked.</p>

        {query.options.length > 0 && (
          <div className="mt-4 grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            {query.options.map((option) => (
              <Field key={option.key} label={option.label}>
                <ListboxSelect
                  aria-label={option.label}
                  value={draft.options[option.key] ?? option.defaultValue}
                  options={option.choices.map((choice) => ({ value: choice.value, label: relabel(choice.label) }))}
                  onChange={(value) => onChange({ ...draft, options: { ...draft.options, [option.key]: value } })}
                />
              </Field>
            ))}
          </div>
        )}

        <div className="mt-4">
          <Field label="Title">
            <Input value={draft.title} maxLength={CHART_TITLE_LIMIT}
                   placeholder={relabel(defaultTitle(query, draft.options))}
                   onChange={(event) => onChange({ ...draft, title: event.target.value })} />
          </Field>
        </div>

        <div className="type-micro-label mb-1.5 mt-5">Preview</div>
        <div className="rounded-u-card border border-u-border bg-u-surface p-5">
          <h3 className="text-lead font-bold">{title}</h3>
          <p className="mt-0.5 text-note text-u-text3">{relabel(optionSummary(query, draft.options))}</p>
          {isPending ? (
            <LinesSkeleton lines={4} className="mt-4" />
          ) : isError ? (
            <p className="mt-4 text-note text-u-text3">Couldn't load a preview. The chart can still be added.</p>
          ) : (
            <DashboardChart result={{ ...result, dimension: relabel(result.dimension) }} chart={chart} />
          )}
        </div>
      </div>
      <DrawerFooter className="flex justify-end gap-2">
        <Button variant="secondary" type="button" onClick={onBack}>
          Back
        </Button>
        <Button type="button" onClick={() => onCommit({ ...draft, chart })}>
          {draft.index === null ? addLabel : "Update chart"}
        </Button>
      </DrawerFooter>
    </>
  );
}
