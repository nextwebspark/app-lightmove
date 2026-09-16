import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ReportMarket, SeniorityLevel, TalentHub } from "../api/types";
import { BarList } from "../components/BarList";
import { HubDrawer } from "../components/HubDrawer";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { MarketSliceDrawer, type SliceSelection } from "../components/MarketSliceDrawer";
import { ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { SectorSeniorityHeatmap } from "../components/SectorSeniorityHeatmap";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import { HubMapPanel } from "../components/HubMapPanel";
import { RelevanceMixCard } from "../components/RelevanceMixCard";
import { marketStats, TOP_HUBS } from "../lib/marketStats";

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
  const [hub, setHub] = useState<TalentHub | null>(null);
  const unplaced = market.withoutSector + market.withoutSeniority;
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

  return (
    <ReportSection
      id="market"
      ordinal="02"
      eyebrow="Shape of the market"
      heading={
        stats.deepest ? (
          <>
            <Figure>{stats.deepest.sector} leads</Figure> the universe, deepest at {stats.deepest.level}; Board-level
            coverage is <Figure>{stats.boardTotal} executives</Figure> across {market.sectors.length} sectors, with{" "}
            {stats.emptyCells} of {stats.totalCells} sector × seniority pockets still empty.
          </>
        ) : (
          <>No executive has both a sector and a seniority on file yet, so the matrix has nothing to place.</>
        )
      }
      lede={
        <>
          {stats.placed} executives placed against {universeCount} companies
          {unplaced > 0 ? ` — ${unplaced} more sit outside the matrix, with no universe company or no seniority on file` : ""}
          . Hatched cells have no executive yet. Select any cell to open the slice.
        </>
      }
    >
      <KpiTileRow>
        <KpiTile
          label="Deepest pocket"
          value={stats.deepest?.count ?? "—"}
          sub={stats.deepest ? `${stats.deepest.sector} · ${stats.deepest.level}` : "nothing placed yet"}
        />
        <KpiTile label="Empty pockets" value={stats.emptyCells} unit={`/ ${stats.totalCells}`} sub="sector × seniority pairs" />
        <KpiTile label="Board-level total" value={stats.boardTotal} valueClass={stats.boardTotal === 0 ? "text-red" : undefined} sub={`across all ${market.sectors.length} sectors`} />
        <KpiTile label="Outside the matrix" value={unplaced} sub={`${market.withoutSector} without a sector · ${market.withoutSeniority} without a level`} />
      </KpiTileRow>

      <ReportCard title="Sector × seniority" caption="executives mapped · darker = more · hatched = none yet · click a cell for detail">
        {market.sectors.length > 0 ? (
          <SectorSeniorityHeatmap sectors={market.sectors} rows={stats.rows} onSelect={handleCell} />
        ) : (
          <div className="py-[30px] text-center text-[12.5px] text-text3">No executive is mapped at a universe company yet.</div>
        )}
      </ReportCard>

      <ReportCard
        title="Where talent sits"
        caption={`executives by hub · ${stats.located} located${market.unlocated > 0 ? ` · ${market.unlocated} with no city on file` : ""} · click a hub for detail`}
        action={
          <Link to={`/projects/${projectId}/companies/universe`} className="inline-flex items-center gap-1.5 text-xs font-medium text-sky hover:underline">
            Open on the map
            <Icon d={ICONS.arrowRight} size={13} />
          </Link>
        }
        note={
          market.hubs.length > 0 ? (
            <>
              <b>{stats.topHubsPct}%</b> of located talent sits in {Math.min(TOP_HUBS, market.hubs.length)} hubs —{" "}
              {stats.topHubs.join(", ")}. Efficient to work, but thin coverage outside the core hubs is a blind spot worth
              closing.
              {market.elsewhere > 0 ? ` ${market.elsewhere} more sit in places past this list.` : ""}
            </>
          ) : (
            "Nobody has a city on file yet."
          )
        }
      >
        <div className="mt-2.5 grid items-center gap-5 lg:grid-cols-[1.2fr_1fr]">
          {mapToken && (
            <HubMapPanel
              hubs={market.hubs}
              accessToken={mapToken}
              selectedCity={hub?.city ?? null}
              onSelect={(city) => setHub(market.hubs.find((h) => h.city === city) ?? null)}
            />
          )}
          <BarList
            rows={market.hubs.map((h, i) => ({
              key: hubKey(h),
              label: h.city,
              count: h.count,
              fillClass: i < TOP_HUBS ? "bg-sky" : "bg-text3",
              title: `${h.count} executives in ${h.city} · click for detail`,
            }))}
            onSelect={(row) => setHub(market.hubs.find((h) => hubKey(h) === row.key) ?? null)}
          />
        </div>
      </ReportCard>

      <ReportCard title="Companies by sector" caption={`the universe · n = ${universeCount}`}>
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

      <RelevanceMixCard universeCount={universeCount} />

      <MarketSliceDrawer selection={slice} projectId={projectId} onClose={() => setSlice(null)} />
      <HubDrawer hub={hub} located={stats.located} projectId={projectId} onClose={() => setHub(null)} />
    </ReportSection>
  );
}

const hubKey = (hub: TalentHub) => `${hub.city}|${hub.country ?? ""}`;
