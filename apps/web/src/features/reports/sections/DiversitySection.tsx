import { useState } from "react";
import { Select } from "../../../components/ui";
import type { ReportDiversity } from "../api/types";
import { BarList } from "../components/BarList";
import { GenderPyramid } from "../components/GenderPyramid";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { NationalityDots } from "../components/NationalityDots";
import { ChartLegend, ReportCard } from "../components/ReportCard";
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

/** 04 — what does the mapped pool look like? Gender in aggregate only; nationality in full. */
export function DiversitySection({ diversity }: { diversity: ReportDiversity }) {
  const [nationality, setNationality] = useState(ALL_NATIONALITIES_FILTER);
  const [level, setLevel] = useState(ALL_LEVELS_FILTER);
  const stats = diversityStats(diversity);
  const fit = feasibility(diversity, stats, { nationality, level });
  const qualifying = useCountUp(fit.qualifying);
  const share = useCountUp(percent(fit.qualifying, fit.scope));
  const femalePct = percent(stats.femaleTotal, stats.total);
  const gccPct = percent(stats.gccTotal, stats.total);
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
      eyebrow="Diversity & DEI"
      heading={
        <>
          Female representation thins from <Figure>{stats.femalePctByLevel["N-2"]}% at N-2</Figure> to{" "}
          <Figure>
            {stats.thinnestPct}% at {stats.thinnestLevel}
          </Figure>
          . <Figure>{stats.nationalityCount} nationalities</Figure> are represented and none holds a majority —{" "}
          {stats.largestNationality} is the largest at {stats.largestPct}%.
        </>
      }
      lede="Gender is shown aggregate-only — no individual is identifiable. Nationality is shown in full because it is public, objective data with real regulatory weight in the GCC (Nitaqat, Emiratisation), not because any one nationality is the target."
    >
      <KpiTileRow>
        <KpiTile label="Female · overall" value={femalePct} unit="%" sub={`${stats.femaleTotal} of ${stats.total} executives`} />
        <KpiTile label={`Female · ${stats.thinnestLevel}`} value={stats.thinnestPct} unit="%" valueClass="text-red" sub="the thinnest level" />
        <KpiTile label="Nationalities" value={stats.nationalityCount} sub={`largest: ${stats.largestNationality} (${stats.largestPct}%)`} />
        <KpiTile label="GCC nationals" value={gccPct} unit="%" sub={`${stats.gccTotal} of ${stats.total} executives`} />
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
              {levelFilterOptions().map((l) => (
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
            sub={`of ${level === ALL_LEVELS_FILTER ? "the full universe" : `${level} executives`}`}
          />
        </KpiTileRow>
        <div className="mb-2.5 mt-[18px] font-mono text-[11px] text-text3">
          {nationality} — where they sit by level · one square per executive
        </div>
        <NationalityDots feasibility={fit} />
      </ReportCard>

      <ReportCard
        title="Nationality mix"
        caption={`full breakdown · n = ${stats.total} · GCC nationals in amber`}
        note={
          <>
            {stats.nationalityCount} nationalities represented, no single group a majority. GCC nationals (
            {diversity.gccNationalities.join(" and ")}) are {stats.gccTotal} of {stats.total} ({gccPct}%) — a fact worth
            knowing, not the lens this view leads with.
          </>
        }
      >
        <StackedBar
          className="mt-4"
          segments={[
            { label: "GCC nationals", count: stats.gccTotal, fillClass: "bg-amber" },
            { label: "Expatriate", count: stats.total - stats.gccTotal, fillClass: "bg-line" },
          ]}
        />
        <div className="mt-4">
          <BarList
            rows={stats.nationalityTotals.map((n) => ({
              key: n.nationality,
              label: n.nationality,
              count: n.count,
              fillClass: n.isGcc ? "bg-amber" : "bg-text3",
            }))}
          />
        </div>
      </ReportCard>

      <ReportCard
        title="Gender through the seniority pipeline"
        caption={`${femalePct}% female overall · aggregate only, no individual identifiable`}
        note={
          <>
            Bar <b>length</b> is scaled to headcount, not just share, so a small level is not drawn the same size as a
            big one — Board's {stats.femalePctByLevel.Board}% female is {diversity.femaleByLevel.Board} person; N-2's{" "}
            {stats.femalePctByLevel["N-2"]}% is {diversity.femaleByLevel["N-2"]}.
          </>
        }
      >
        <ChartLegend
          items={[
            { label: "Female", swatchClass: "bg-sky" },
            { label: "Male", swatchClass: "bg-line" },
          ]}
        />
        <GenderPyramid diversity={diversity} stats={stats} />
      </ReportCard>
    </ReportSection>
  );
}
