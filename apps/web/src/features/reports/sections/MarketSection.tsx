import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { Hub, ReportMarket, SeniorityLevel } from "../api/types";
import { BarList } from "../components/BarList";
import { HubDrawer } from "../components/HubDrawer";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { MarketSliceDrawer, type SliceSelection } from "../components/MarketSliceDrawer";
import { ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { SectorSeniorityHeatmap } from "../components/SectorSeniorityHeatmap";
import { StackedBar } from "../components/StackedBar";
import { percent } from "../lib/figures";
import { marketStats, TOP_HUBS } from "../lib/marketStats";

const RELEVANCE_FILL: Record<string, string> = {
  Direct: "bg-sky",
  Adjacent: "bg-amber",
  "AI inferred": "bg-text3",
};

/** 02 — where does the universe actually sit? Sector by seniority, then by hub. */
export function MarketSection({
  market,
  universeCount,
  projectId,
}: {
  market: ReportMarket;
  universeCount: number;
  projectId: string;
}) {
  const stats = marketStats(market);
  const [slice, setSlice] = useState<SliceSelection | null>(null);
  const [hub, setHub] = useState<Hub | null>(null);
  const direct = market.relevance.find((r) => r.label === "Direct")?.count ?? 0;

  const handleCell = (sector: string, level: SeniorityLevel) =>
    setSlice({
      sector,
      level,
      count: market.cells.find((c) => c.sector === sector && c.level === level)?.count ?? 0,
      slice: market.slices.find((s) => s.sector === sector && s.level === level),
    });

  return (
    <ReportSection
      id="market"
      ordinal="02"
      eyebrow="Shape of the market"
      heading={
        <>
          <Figure>{stats.deepest.sector} dominates</Figure> the universe and seniority is healthy at C-Suite and N-1 — but{" "}
          <Figure>Board is thin</Figure>, with {stats.boardTotal} executives across all {market.sectors.length} sectors.
        </>
      }
      lede={
        <>
          {stats.executivesMapped} executives mapped against {universeCount} companies. The matrix shows where they sit
          and where the gaps are — hatched cells have no executive yet. Select any cell to open the slice.
        </>
      }
    >
      <KpiTileRow>
        <KpiTile label="Deepest pocket" value={stats.deepest.count} sub={`${stats.deepest.sector} · ${stats.deepest.level}`} />
        <KpiTile label="Empty cells" value={stats.emptyCells} unit={`/ ${stats.totalCells}`} sub="sector × seniority pairs" />
        <KpiTile label="Board-level total" value={stats.boardTotal} valueClass="text-red" sub={`across all ${market.sectors.length} sectors`} />
        <KpiTile label="Direct relevance" value={direct} unit={`/ ${universeCount}`} sub={`${percent(direct, universeCount)}% of the universe`} />
      </KpiTileRow>

      <ReportCard title="Sector × seniority" caption="executives mapped · darker = more · hatched = none yet · click a cell for detail">
        <SectorSeniorityHeatmap sectors={market.sectors} rows={stats.rows} onSelect={handleCell} />
      </ReportCard>

      <ReportCard
        title="Where talent sits"
        caption={`executives by hub · ${stats.hubTotal} across ${market.hubs.length} hubs · click a hub for detail`}
        action={
          <Link to={`/projects/${projectId}/companies/universe`} className="inline-flex items-center gap-1.5 text-xs font-medium text-sky hover:underline">
            Open on the map
            <Icon d={ICONS.arrowRight} size={13} />
          </Link>
        }
        note={
          <>
            <b>{stats.topHubsPct}%</b> of mapped talent sits in just {TOP_HUBS} hubs — {stats.topHubs.join(", ")}. Efficient
            to work, but thin coverage outside the core hubs is a blind spot worth closing.
          </>
        }
      >
        <div className="mt-2.5">
          <BarList
            rows={market.hubs.map((h, i) => ({
              key: h.city,
              label: h.city,
              count: h.count,
              fillClass: i < TOP_HUBS ? "bg-sky" : "bg-text3",
              title: `${h.count} executives in ${h.city} · click for detail`,
            }))}
            onSelect={(row) => setHub(market.hubs.find((h) => h.city === row.key) ?? null)}
          />
        </div>
      </ReportCard>

      <div className="grid gap-3.5 md:grid-cols-2">
        <ReportCard title="Companies by sector" caption={`target universe · n = ${universeCount}`}>
          <div className="mt-2.5">
            <BarList
              rows={market.companiesBySector.map((b, i) => ({
                key: b.label,
                label: b.label,
                count: b.count,
                fillClass: i === 0 ? "bg-sky" : "bg-text3",
              }))}
            />
          </div>
        </ReportCard>
        <ReportCard
          title="Relevance mix"
          caption="how directly each mapped company competes for this mandate"
          note="AI-inferred adjacencies carry the lowest confidence of the three tiers — lift it before they feed a shortlist."
        >
          <StackedBar
            className="mt-4"
            segments={market.relevance.map((r) => ({ label: r.label, count: r.count, fillClass: RELEVANCE_FILL[r.label] ?? "bg-line" }))}
          />
        </ReportCard>
      </div>

      <MarketSliceDrawer selection={slice} projectId={projectId} onClose={() => setSlice(null)} />
      <HubDrawer hub={hub} hubTotal={stats.hubTotal} projectId={projectId} onClose={() => setHub(null)} />
    </ReportSection>
  );
}
