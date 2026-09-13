import { useState } from "react";
import { Select } from "../../../components/ui";
import type { ReportDiversity } from "../api/types";
import { BarList } from "../components/BarList";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { NationalityDots } from "../components/NationalityDots";
import { ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { StackedBar } from "../components/StackedBar";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  GCC_NATIONALS_FILTER,
  levelFilterOptions,
  nationalityFilterOptions,
} from "../lib/diversityStats";
import { percent } from "../lib/figures";
import { useCountUp } from "../lib/useCountUp";

const SELECT_CLASS = "w-auto bg-panel py-[7px] text-[12.5px] font-medium";

/** 04 — who is in the mapped pool, by nationality. Gender is not recorded, so nothing here claims it. */
export function DiversitySection({ diversity }: { diversity: ReportDiversity }) {
  const [nationality, setNationality] = useState(ALL_NATIONALITIES_FILTER);
  const [level, setLevel] = useState(ALL_LEVELS_FILTER);
  const stats = diversityStats(diversity);
  const fit = feasibility(diversity, stats, { nationality, level });
  const qualifying = useCountUp(fit.qualifying);
  const share = useCountUp(percent(fit.qualifying, fit.scope));
  const requirement =
    nationality === GCC_NATIONALS_FILTER
      ? "a GCC national"
      : nationality === ALL_NATIONALITIES_FILTER
        ? "any nationality"
        : `a ${nationality} national`;
  const levelLabel = level === ALL_LEVELS_FILTER ? "any level" : level;

  return (
    <ReportSection
      id="dei"
      ordinal="04"
      eyebrow="Nationality & localisation"
      heading={
        stats.largest ? (
          <>
            <Figure>{stats.nationalityCount} nationalities</Figure> are represented and{" "}
            {stats.largestPct > 50 ? (
              <>
                <Figure>{stats.largest.nationality}</Figure> holds a majority at {stats.largestPct}%
              </>
            ) : (
              <>
                none holds a majority — {stats.largest.nationality} is the largest at {stats.largestPct}%
              </>
            )}
            . GCC nationals are <Figure>{stats.gccPct}%</Figure> of the pool.
          </>
        ) : (
          <>Nobody has a nationality on file yet, so there is no mix to report.</>
        )
      }
      lede="Nationality is shown in full because it is public, objective data with real regulatory weight in the GCC (Nitaqat, Emiratisation). Gender is not recorded on a candidate and is not inferred here — a guess stated as a finding would be worse than none."
    >
      <KpiTileRow>
        <KpiTile label="Nationalities" value={stats.nationalityCount} sub={stats.largest ? `largest: ${stats.largest.nationality} (${stats.largestPct}%)` : "none on file"} />
        <KpiTile label="GCC nationals" value={stats.gccPct} unit="%" sub={`${diversity.gccNationals} of ${stats.total} with a nationality on file`} />
        <KpiTile
          label="Nationality unknown"
          value={diversity.unknownNationality}
          valueClass={diversity.unknownNationality > 0 ? "text-amber" : undefined}
          sub="executives with none on file"
        />
      </KpiTileRow>

      <ReportCard
        title="Nationality feasibility"
        caption="how many mapped executives qualify against a nationality requirement"
        action={
          <>
            <Select aria-label="Nationality requirement" value={nationality} onChange={(e) => setNationality(e.target.value)} className={SELECT_CLASS}>
              {nationalityFilterOptions(diversity).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Select>
            <Select aria-label="Seniority level" value={level} onChange={(e) => setLevel(e.target.value)} className={SELECT_CLASS}>
              {levelFilterOptions(diversity).map((l) => (
                <option key={l} value={l}>
                  {l}
                </option>
              ))}
            </Select>
          </>
        }
        note={
          <>
            A client requiring <b>{requirement}</b> at {levelLabel} can realistically draw from <b>{fit.qualifying}</b> of
            the {fit.scope} executives mapped in that scope.
          </>
        }
      >
        <KpiTileRow className="mt-3.5 [grid-template-columns:1fr_1fr]">
          <KpiTile
            className="bg-panel"
            label="Qualifying executives"
            value={Math.round(qualifying)}
            unit={`/ ${fit.scope}`}
            valueClass="text-sky"
            sub={`${nationality} · ${level === ALL_LEVELS_FILTER ? "all levels" : level}`}
          />
          <KpiTile
            className="bg-panel"
            label="Share of this scope"
            value={Math.round(share)}
            unit="%"
            sub={`of ${level === ALL_LEVELS_FILTER ? "everyone with a level on file" : `${level} executives`}`}
          />
        </KpiTileRow>
        <div className="mb-2.5 mt-[18px] font-mono text-[11px] text-text3">
          {nationality} — where they sit by level · one square per executive
        </div>
        <NationalityDots feasibility={fit} />
      </ReportCard>

      <ReportCard
        title="Nationality mix"
        caption={`n = ${stats.total} with a nationality on file · GCC nationals in amber`}
        note={
          <>
            GCC nationals are {diversity.gccNationals} of {stats.total} ({stats.gccPct}%) — the figure a localisation
            quota is measured against, not the lens this view leads with.
          </>
        }
      >
        <StackedBar
          className="mt-4"
          segments={[
            { label: "GCC nationals", count: diversity.gccNationals, fillClass: "bg-amber" },
            { label: "Expatriate", count: stats.total - diversity.gccNationals, fillClass: "bg-line" },
          ]}
        />
        <div className="mt-4">
          <BarList
            rows={diversity.nationalities.map((row) => ({
              key: row.nationality,
              label: row.nationality,
              count: row.total,
              fillClass: row.gcc ? "bg-amber" : "bg-text3",
            }))}
          />
        </div>
      </ReportCard>
    </ReportSection>
  );
}
