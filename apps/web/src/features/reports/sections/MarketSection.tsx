import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import type { ReportMarket, SeniorityLevel, TalentHub } from "../api/types";
import { BarList } from "../components/BarList";
import { ChartEmpty } from "../components/ChartEmpty";
import { HubDrawer } from "../components/HubDrawer";
import { HubMapPanel } from "../components/HubMapPanel";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { MarketSliceDrawer, type SliceSelection } from "../components/MarketSliceDrawer";
import { ReportCard } from "../components/ReportCard";
import { ReportSection } from "../components/ReportSection";
import { SectorSeniorityHeatmap } from "../components/SectorSeniorityHeatmap";
import { SectorTreemap } from "../components/SectorTreemap";
import { percent } from "../lib/figures";
import { marketStats, TOP_HUBS } from "../lib/marketStats";

/** Where does the universe actually sit? Sector by seniority, then by country. */
export function MarketSection({
  market,
  universeCount,
  executivesMapped,
  currency,
  projectId,
}: {
  market: ReportMarket;
  universeCount: number;
  executivesMapped: number;
  /** The report's currency, for the median a country's drawer states. */
  currency: string;
  projectId: string;
}) {
  const stats = marketStats(market);
  const [slice, setSlice] = useState<SliceSelection | null>(null);
  const [hub, setHub] = useState<TalentHub | null>(null);
  const highestMedian = Math.max(0, ...market.hubs.map((h) => h.medianPackage ?? 0));
  // Whether this deployment offers a map at all. Cached for the session: it is deployment config,
  // not mandate data, and it cannot change while the reader is on the page.
  const mapConfig = useQuery({
    queryKey: talentMapApi.TALENT_MAP_CONFIG_KEY,
    queryFn: ({ signal }) => talentMapApi.getTalentMapConfig(signal),
    staleTime: Infinity,
  });
  const mapToken = mapConfig.data?.enabled ? mapConfig.data.publicToken : null;

  const handleCell = (sector: string, level: SeniorityLevel) =>
    setSlice({
      sector,
      level,
      count: market.cells.find((c) => c.sector === sector && c.level === level)?.count ?? 0,
      slice: market.slices.find((s) => s.sector === sector && s.level === level),
    });
  const handleHub = (country: string) => setHub(market.hubs.find((h) => h.country === country) ?? null);

  return (
    <ReportSection
      question="Where does the universe actually sit?"
      lede={
        stats.deepest ? (
          <>
            <b>{stats.deepest.sector} leads</b> the universe, deepest at {stats.deepest.level}.{" "}
            {stats.boardTotal === 0 ? (
              <b>Nobody is mapped at Board level</b>
            ) : (
              <>
                Board-level coverage is <b>{stats.boardTotal} executives</b>
              </>
            )}{" "}
            across {market.sectors.length} sectors, and {stats.emptyCells} of {stats.totalCells} sector × seniority
            pockets are still empty.
          </>
        ) : (
          "No executive has both a sector and a seniority on file yet, so the matrix has nothing to place."
        )
      }
    >
      <KpiTileRow>
        <KpiTile
          tone="lead"
          label="Deepest pocket"
          value={stats.deepest?.count ?? "—"}
          sub={stats.deepest ? `${stats.deepest.sector} · ${stats.deepest.level}` : "nothing placed yet"}
        />
        <KpiTile label="Empty pockets" value={stats.emptyCells} unit={`/${stats.totalCells}`} sub="sector × seniority pairs" />
        <KpiTile tone="alarm" label="Board-level total" value={stats.boardTotal} sub={`across all ${market.sectors.length} sectors`} />
        <KpiTile
          tone="positive"
          label="Placed in the matrix"
          value={stats.placed}
          unit={`/${executivesMapped}`}
          sub={`${percent(stats.placed, executivesMapped)}% of mapped executives`}
        />
      </KpiTileRow>

      <ReportCard
        title="Sector × seniority"
        caption={`${stats.placed} executives placed · hatched = no executive identified yet · click a cell for detail`}
        note={
          market.withoutSector + market.withoutSeniority > 0 ? (
            <>
              Only an executive with both a sector and a level can sit in a cell: <b>{market.withoutSector}</b> are mapped
              at no universe company and <b>{market.withoutSeniority}</b> have no seniority on file.
            </>
          ) : undefined
        }
      >
        {stats.rows.length > 0 ? (
          <SectorSeniorityHeatmap sectors={stats.sectors} rows={stats.rows} onSelect={handleCell} />
        ) : (
          <ChartEmpty>
            {market.sectors.length === 0
              ? "No executive is mapped at a universe company yet."
              : "No executive mapped at a universe company has a seniority on file yet."}
          </ChartEmpty>
        )}
      </ReportCard>

      <ReportCard
        title="Where talent sits"
        caption={`geographic concentration · ${stats.located} located across ${market.hubs.length} countries${market.unlocated > 0 ? ` · ${market.unlocated} with no country on file` : ""} · click a country for detail`}
        note={
          market.hubs.length > 0 ? (
            <>
              <b>{stats.topHubsPct}%</b> of located talent sits in {Math.min(TOP_HUBS, market.hubs.length)} countries —{" "}
              {stats.topHubs.join(", ")}. Efficient to work, but thin coverage outside the core markets is a blind spot
              worth closing.
              {market.elsewhere > 0 ? ` ${market.elsewhere} more sit in places past this list.` : ""}
            </>
          ) : (
            "Nobody has a country on file yet."
          )
        }
      >
        <div className="mt-3.5 grid items-center gap-6 lg:grid-cols-[1.2fr_1fr]">
          {mapToken && (
            <HubMapPanel hubs={market.hubs} accessToken={mapToken} selectedCountry={hub?.country ?? null} onSelect={handleHub} />
          )}
          <BarList
            rows={market.hubs.map((h) => ({
              key: h.country,
              label: h.country,
              count: h.count,
              title: `${h.count} executives in ${h.country} · click for detail`,
            }))}
            onSelect={(row) => handleHub(row.key)}
          />
        </div>
      </ReportCard>

      <SectorTreemap rows={market.companiesBySector} universeCount={universeCount} />

      <MarketSliceDrawer selection={slice} projectId={projectId} onClose={() => setSlice(null)} />
      <HubDrawer
        hub={hub}
        located={stats.located}
        currency={currency}
        highestMedian={highestMedian}
        projectId={projectId}
        onClose={() => setHub(null)}
      />
    </ReportSection>
  );
}
