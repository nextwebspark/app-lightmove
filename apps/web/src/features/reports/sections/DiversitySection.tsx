import { useState } from "react";
import { Select } from "../../../components/ui";
import type { ReportDiversity } from "../api/types";
import { BarList } from "../components/BarList";
import { GenderPyramid } from "../components/GenderPyramid";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { LockedBenchmarkCard } from "../components/LockedBenchmarkCard";
import { NationalityDonut } from "../components/NationalityDonut";
import { NationalityDots } from "../components/NationalityDots";
import { ChartLegend, ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { StackedBar } from "../components/StackedBar";
import {
  ALL_LEVELS_FILTER,
  ALL_NATIONALITIES_FILTER,
  diversityStats,
  feasibility,
  genderStats,
  GCC_NATIONALS_FILTER,
  levelFilterOptions,
  nationalityFilterOptions,
} from "../lib/diversityStats";
import { percent } from "../lib/figures";
import { useCountUp } from "../lib/useCountUp";

const SELECT_CLASS = "w-auto bg-panel py-[7px] text-[12.5px] font-medium";

/** 04 — who is in the mapped pool, by nationality and by gender where one was recorded. */
export function DiversitySection({ diversity }: { diversity: ReportDiversity }) {
  const [nationality, setNationality] = useState(ALL_NATIONALITIES_FILTER);
  const [level, setLevel] = useState(ALL_LEVELS_FILTER);
  const stats = diversityStats(diversity);
  const gender = genderStats(diversity);
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
      eyebrow="Diversity & DEI"
      heading={
        <>
          {gender.thinnest && (
            <>
              Women are <Figure>{gender.femalePct}% of the recorded pool</Figure> and thinnest at{" "}
              <Figure>
                {gender.thinnest.level} ({gender.thinnest.femalePct}%)
              </Figure>
              .{" "}
            </>
          )}
          {stats.largest ? (
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
          )}
        </>
      }
      lede={
        gender.recorded === 0
          ? "Nationality is shown in full because it is public, objective data with real regulatory weight in the GCC (Nitaqat, Emiratisation). Gender is counted only where a researcher recorded it, and nobody has recorded one on this mandate yet — nothing here infers it from a name."
          : `Nationality is shown in full because it is public, objective data with real regulatory weight in the GCC (Nitaqat, Emiratisation). Gender is counted from the ${gender.recorded} executives who have one on file${gender.unrecorded > 0 ? `, with ${gender.unrecorded} not recorded` : ""} — never inferred from a name.`
      }
    >
      <KpiTileRow>
        <KpiTile
          label="Female · overall"
          value={gender.recorded === 0 ? "—" : gender.femalePct}
          unit={gender.recorded === 0 ? undefined : "%"}
          sub={gender.recorded === 0 ? "no gender recorded yet" : `${gender.female} of ${gender.recorded} recorded`}
        />
        <KpiTile
          label={gender.thinnest ? `Female · ${gender.thinnest.level}` : "Female · thinnest level"}
          value={gender.thinnest ? gender.thinnest.femalePct : "—"}
          unit={gender.thinnest ? "%" : undefined}
          valueClass={gender.thinnest && gender.thinnest.femalePct < gender.femalePct ? "text-red" : undefined}
          sub={gender.thinnest ? `the thinnest level · ${gender.thinnest.recorded} recorded` : "nothing recorded yet"}
        />
        <KpiTile
          label="Nationalities"
          value={stats.nationalityCount}
          sub={
            stats.largest
              ? `largest: ${stats.largest.nationality} (${stats.largestPct}%)${diversity.unknownNationality > 0 ? ` · ${diversity.unknownNationality} unknown` : ""}`
              : "none on file"
          }
        />
        <KpiTile label="GCC nationals" value={stats.gccPct} unit="%" sub={`${diversity.gccNationals} of ${stats.total} with a nationality on file`} />
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
        <NationalityDonut rows={diversity.nationalities} total={stats.total} largest={stats.largest} />
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

      <ReportCard
        title="Gender through the seniority pipeline"
        caption={
          gender.recorded === 0
            ? "no gender recorded on this mandate yet"
            : `${gender.femalePct}% female of ${gender.recorded} recorded · aggregate only, no individual identifiable`
        }
        note={
          gender.recorded === 0 ? (
            <>
              Gender is recorded on an executive's profile and is never guessed from a name, so this
              chart stays empty until somebody records one. Set it in the Background section of a
              profile and this fills in.
            </>
          ) : (
            <>
              Bar <b>length</b> is scaled to headcount, not share, so a small level is not drawn the
              size of a big one. Every share divides by the executives <b>recorded</b> at that level
              {gender.unrecorded > 0
                ? `, not by its headcount — ${gender.unrecorded} across the map have no gender on file`
                : ""}
              .
            </>
          )
        }
      >
        {gender.recorded === 0 ? (
          <div className="py-[30px] text-center text-[12.5px] text-text3">
            Nobody on this mandate has a gender recorded.
          </div>
        ) : (
          <>
            <ChartLegend
              items={[
                { label: "Female", swatchClass: "bg-sky" },
                { label: "Male", swatchClass: "bg-line" },
              ]}
            />
            <GenderPyramid stats={gender} />
          </>
        )}
      </ReportCard>

      <LockedBenchmarkCard>
        <b className="text-text">Cross-mandate diversity benchmark — not built.</b> Every figure
        above is this mandate's own pool, so nothing here says whether that mix is normal for the
        sector and seniority. Comparing across mandates is a later piece of work.
      </LockedBenchmarkCard>
    </ReportSection>
  );
}
