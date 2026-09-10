import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Spinner } from "../../../components/ui";
import { EmptyState } from "../../../components/ui/EmptyState";
import { Skeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../../triage/api/types";
import type { TalentMapPage } from "../api/types";
import { countOf, toFeatureCollection } from "../lib/talentMapFeatures";
import {
  buildTree,
  filterTree,
  nodesOf,
  pathTo,
  UNLOCATED_KEY,
  type TreeCompany,
  type TreeExecutive,
} from "../lib/talentMapTree";
import type { TalentMapPreferences } from "../lib/useTalentMapPreferences";
import { TalentMapPopup } from "./TalentMapPopup";
import { TalentMapTree } from "./TalentMapTree";

/**
 * Lazy, so the table view never downloads a map library: `mapbox-gl` is imported by this one module
 * and nothing else, and a test that renders the view mocks this import rather than the library.
 */
const TalentMapGlobe = lazy(() => import("./TalentMapGlobe"));

/**
 * The Companies screen read as a globe: the mapping panel on the left, the globe beside it, and the
 * existing drawers on demand — opened from a row or a pin exactly as the grid opens them.
 *
 * <p>Selection and hover live here and are handed to both halves, so a row and its pin are one
 * thing touched two ways. The tree is built from the page once and narrowed by the toolbar's search
 * on every keystroke; the globe draws whatever the narrowed tree holds.
 */
export function TalentMapView({
  projectId,
  page,
  query,
  accessToken,
  canWrite,
  loading,
  error,
  preferences,
  onPreferences,
  onOpenCompany,
  onOpenCandidate,
  onAddExecutive,
}: {
  projectId: string;
  page: TalentMapPage | undefined;
  query: string;
  accessToken: string;
  canWrite: boolean;
  loading: boolean;
  error: boolean;
  preferences: TalentMapPreferences;
  onPreferences: (changes: Partial<TalentMapPreferences>) => void;
  onOpenCompany: (company: TriageCompany) => void;
  onOpenCandidate: (candidate: Candidate) => void;
  onAddExecutive: (company: TriageCompany) => void;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [hoveredId, setHoveredId] = useState<string | null>(null);
  const [unsupported, setUnsupported] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());

  const tree = useMemo(() => (page ? buildTree(page) : null), [page]);
  const visible = useMemo(() => (tree ? filterTree(tree, query) : null), [tree, query]);
  const features = useMemo(
    () =>
      visible
        ? toFeatureCollection(visible, preferences.showExecutives)
        : { type: "FeatureCollection" as const, features: [] },
    [visible, preferences.showExecutives],
  );
  const nodesById = useMemo(() => {
    const index = new Map<string, TreeCompany | TreeExecutive>();
    if (tree) for (const node of nodesOf(tree)) index.set(node.id, node);
    return index;
  }, [tree]);

  // Countries open by default — the reference's reading — and companies closed behind their count.
  // Only additive on a refetch, so a country the reader folded stays folded.
  useEffect(() => {
    if (!tree) return;
    setExpanded((current) => {
      const next = new Set(current);
      for (const country of tree.countries) if (!current.has(`seen:${country.key}`)) {
        next.add(country.key);
        next.add(`seen:${country.key}`);
      }
      return next;
    });
  }, [tree]);

  // A row that left the page (removed, moved, filtered out) cannot stay selected.
  useEffect(() => {
    if (selectedId && !nodesById.has(selectedId)) setSelectedId(null);
  }, [selectedId, nodesById]);

  const select = (id: string | null) => {
    setSelectedId(id);
    if (!id || !tree) return;
    const path = pathTo(tree, id);
    setExpanded((current) => {
      const next = new Set(current);
      if (path.country) next.add(path.country);
      if (path.company) next.add(path.company);
      return next;
    });
  };

  const toggle = (key: string) =>
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const open = (node: TreeCompany | TreeExecutive) => {
    if (node.kind === "company") onOpenCompany(node.company);
    else onOpenCandidate(node.candidate);
  };

  const collapsed = preferences.panelCollapsed;
  const counts = visible?.counts;

  return (
    <div className="relative flex min-h-0 min-w-0 flex-1 flex-col lg:flex-row">
      {/* The mapping panel: a side panel from lg up, a sheet over the globe below it. */}
      <aside
        aria-label="Mapping panel"
        className={cn(
          "absolute inset-x-0 bottom-0 z-10 flex max-h-[62%] flex-col border-t border-line bg-panel shadow-panel",
          "lg:static lg:max-h-none lg:flex-none lg:border-e lg:border-t-0 lg:shadow-none lg:transition-[width]",
          collapsed ? "lg:w-10" : "lg:w-[300px]",
        )}
      >
        <div className={cn("flex flex-none items-center gap-2 border-b border-line-soft px-2.5 py-2", collapsed && "lg:flex-col lg:px-1")}>
          <button
            type="button"
            onClick={() => onPreferences({ panelCollapsed: !collapsed })}
            aria-expanded={!collapsed}
            aria-label={collapsed ? "Show mapping panel" : "Hide mapping panel"}
            title={collapsed ? "Show mapping panel" : "Hide mapping panel"}
            className="flex-none cursor-pointer rounded-md p-1 text-text3 transition hover:bg-panel2 hover:text-text"
          >
            <Icon d={collapsed ? ICONS.expand : ICONS.collapse} size={15} />
          </button>
          {!collapsed && counts && (
            <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11.5px] text-text2">
              <span className="inline-flex items-center gap-1" title="Countries">
                <Icon d={ICONS.mapPin} size={12} className="text-text3" />
                {counts.countries}
              </span>
              <span className="inline-flex items-center gap-1" title="Companies">
                <Icon d={ICONS.building} size={12} className="text-text3" />
                {counts.companies}
              </span>
              <span className="inline-flex items-center gap-1" title="Executives">
                <Icon d={ICONS.candidates} size={12} className="text-text3" />
                {counts.executives}
              </span>
              {page && page.geocodingPending > 0 && (
                <span role="status" className="inline-flex items-center gap-1 text-text3">
                  <Spinner />
                  Locating {countOf(page.geocodingPending, "place")}…
                </span>
              )}
            </div>
          )}
          {collapsed && counts && (
            <span className="hidden font-mono text-[11px] text-text3 lg:block" title="Companies">
              {counts.companies}
            </span>
          )}
        </div>

        {!collapsed && (
          <div className="min-h-0 flex-1 overflow-y-auto">
            {loading && !visible ? (
              <div className="flex flex-col gap-3 p-3" role="status" aria-label="Loading">
                {Array.from({ length: 7 }, (_, index) => (
                  <Skeleton key={index} className={cn("h-4", index % 3 === 0 ? "w-32" : "ms-4 w-44")} />
                ))}
              </div>
            ) : visible ? (
              <>
                <TalentMapTree
                  tree={visible}
                  projectId={projectId}
                  expanded={expanded}
                  onToggle={toggle}
                  selectedId={selectedId}
                  hoveredId={hoveredId}
                  onSelect={select}
                  onHover={setHoveredId}
                  onOpen={open}
                />
                {counts && counts.companies === 0 && counts.executives === 0 && (
                  <p className="px-3 py-4 text-[12.5px] text-text3">
                    {query ? "Nothing matches that search." : "Nothing mapped yet."}
                  </p>
                )}
              </>
            ) : null}
            {tree && tree.counts.unlocated > 0 && !expanded.has(UNLOCATED_KEY) && (
              <p className="px-3 py-2 font-mono text-[11px] text-text3">
                {tree.counts.unlocated} without a location on the map.
              </p>
            )}
            {page && (page.totalCompanies > page.companies.length || page.totalCandidates > page.candidates.length) && (
              <p role="status" className="px-3 py-2 font-mono text-[11px] text-text3">
                Showing {page.companies.length} of {page.totalCompanies} companies and{" "}
                {page.candidates.length} of {page.totalCandidates} executives.
              </p>
            )}
          </div>
        )}
      </aside>

      <div className="relative min-h-0 min-w-0 flex-1 bg-panel2">
        {error ? (
          <EmptyState
            icon={<Icon d={ICONS.warning} size={22} />}
            title="We couldn't load the map"
            body="The mapping could not be read. Switch back to the table, or try again in a moment."
          />
        ) : unsupported ? (
          <EmptyState
            icon={<Icon d={ICONS.globe} size={22} />}
            title="This browser can't draw the globe"
            body="The map needs WebGL, which this browser has turned off or does not support. The table shows the same mapping."
          />
        ) : (
          <Suspense fallback={<GlobeSkeleton />}>
            <TalentMapGlobe
              accessToken={accessToken}
              features={features}
              selectedId={selectedId}
              hoveredId={hoveredId}
              showExecutives={preferences.showExecutives}
              onSelect={select}
              onHover={setHoveredId}
              onToggleExecutives={() =>
                onPreferences({ showExecutives: !preferences.showExecutives })
              }
              onUnsupported={() => setUnsupported(true)}
              renderPopup={(id) => {
                const node = nodesById.get(id);
                if (!node) return null;
                return (
                  <TalentMapPopup
                    node={node}
                    canWrite={canWrite}
                    onOpen={() => open(node)}
                    onAddExecutive={
                      node.kind === "company" ? () => onAddExecutive(node.company) : undefined
                    }
                    onClose={() => setSelectedId(null)}
                  />
                );
              }}
            />
          </Suspense>
        )}

        {!error && !unsupported && (
          <div
            aria-label="Legend"
            className="pointer-events-none absolute bottom-7 start-2.5 flex items-center gap-3 rounded-[6px] border border-line bg-panel/90 px-2.5 py-1.5 font-mono text-[10.5px] text-text2 backdrop-blur"
          >
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden="true" className="size-2.5 rounded-full bg-text" />
              Company
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden="true" className="size-2 rounded-full bg-sky" />
              Executive
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden="true" className="size-2.5 rounded-full bg-amber-btn" />
              Selected
            </span>
          </div>
        )}

        {loading && !error && !unsupported && (
          <div className="pointer-events-none absolute end-2.5 top-2.5 rounded-[6px] border border-line bg-panel/90 px-2 py-1 font-mono text-[10.5px] text-text3 backdrop-blur">
            Refreshing…
          </div>
        )}

        {visible && features.features.length === 0 && !loading && !error && !unsupported && (
          <div className="pointer-events-none absolute inset-x-0 top-1/3 flex justify-center px-4">
            <div className="max-w-[360px] rounded-[10px] border border-line bg-panel/95 px-4 py-3 text-center text-[12.5px] text-text2 shadow-panel backdrop-blur">
              <div className="font-semibold text-text">Nothing to place on the map yet</div>
              {tree && tree.counts.unlocated > 0
                ? `${tree.counts.unlocated} of the mandate's rows have no city or country the map can use.`
                : "Add a company with a city or country and it will appear here."}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function GlobeSkeleton() {
  return (
    <div role="status" aria-label="Loading map" className="grid size-full place-items-center">
      <Skeleton className="size-[60%] max-w-[560px] rounded-full opacity-70" />
    </div>
  );
}
